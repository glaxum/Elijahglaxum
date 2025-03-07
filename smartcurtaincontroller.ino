#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <String.h>

// Replace with your network credentials
const char* ssid     = "SmartCurtain";
const char* password = "12345678";
// Set web server port number to 80
WiFiServer server(80);

const int LDRPin = 32; 
int LDRValue;

// DHT11 CODE (Temperature and Humidity Sensor)
#include <dht11.h> // Include the dht11 library
const int DHT11PIN = 33; // Set DHT11 to Analog Pin 1
dht11 DHT11; // variable for dht11

// SERVO MOTOR CODE
#include <ESP32Servo.h>  // Include the servo motor library
Servo servo;             // Create the servo object
int servoPin = 4;

// (HC-SR04 MODULE) ULTRASONIC DISTANCE SENSOR CODE 
int trigPin = 13; // Set Trigger to Digital Pin 13
int echoPin = 14; // Set Echo to Digital Pin 14
int distance; // to store the value of distance

// LIMIT SWITCH CODE
int limitSwitch1 = 34;  
int limitSwitch2 = 35;
int switchValue1;
int switchValue2;

enum Mode { MANUAL, AUTOMATIC };
Mode currentMode = AUTOMATIC;

void setup() {
  Serial.begin(921600);
  WiFi.softAP(ssid, password);
  server.begin();
  Serial.println("Access Point started");
  Serial.print("IP address: ");
  Serial.println(WiFi.softAPIP());

  // (HC-SR04 MODULE) ULTRASONIC DISTANCE SENSOR CODE
  pinMode(trigPin, OUTPUT);  //sets pin as OUTPUT
  pinMode(echoPin, INPUT);   //sets pin as INPUT

  pinMode(LDRPin, INPUT);  // Set Photoresistor analog pin as INPUT

  servo.attach(servoPin);  // Attach the servo to digital pin 4
  servo.write(90);         // Set servo to stay still

  // Set analog read resolution and attenuation for ESP32
  analogReadResolution(12); // 12-bit resolution (0-4095)
  analogSetAttenuation(ADC_11db); // 0-3.3V range

  // Setup limit switch pins
  pinMode(limitSwitch1, INPUT_PULLUP);
  pinMode(limitSwitch2, INPUT_PULLUP);
}

void loop() {
  // ANDROID APP CONNECTION AND CONTROL
  WiFiClient client = server.available();
  if (client) {
    String request = "";
    while (client.connected()) {
      if (client.available()) {
        char c = client.read();
        request += c;
        if (c == '\r') {
          // End of line reached, check if next character is newline

          Serial.println(request);  // full HTTP command line including GET  and HTTP 1

          // Extract command from request string
          int start = request.indexOf("GET /") + 5;
          int end = request.indexOf("HTTP/");
          String command = request.substring(start, end);

          // Purify the command
          command.replace("\n", "");
          command.replace("\r", "");
          command.replace(" ", ""); // removes all space characters
          command.replace("\t", ""); // removes all tab characters
          command.trim();

          // Handle commands
          if (command.equals("open")) {
              Serial.println("Open Curtain command received");
              openCurtain();
          } else if (command.equals("close")) {
              Serial.println("Close Curtain command received");
              closeCurtain();    
          } else if (command.equals("switch")) {
              if (currentMode == AUTOMATIC) {
                  currentMode = MANUAL;
                  Serial.println("Set to Manual Mode");
              } else {
                  currentMode = AUTOMATIC;
                  Serial.println("Set to Automatic Mode");
              }
          }

          if (client.peek() == '\n') {
            // Read temperature, humidity, and light values
            int checkDht11Value = DHT11.read(DHT11PIN);
            int temperature = DHT11.temperature;
            int humidity = DHT11.humidity;
            int lightValue = analogRead(LDRPin);

            // Create a response message that includes the sensor data in JSON format
            String responseMessage = "{";
            responseMessage += "\"temperature\":" + String(temperature) + ",";
            responseMessage += "\"humidity\":" + String(humidity) + ",";
            responseMessage += "\"lightValue\":" + String(lightValue);
            responseMessage += "}";

            client.println("HTTP/1.1 200 OK");
            client.println("Content-type:application/json");
            client.println();
            client.println(responseMessage);
            break;
          }
        }
      }
    }
  }

  // Print sensor values to the Serial Monitor for debugging
  LDRValue = analogRead(LDRPin);
  Serial.print("Light Resistance: ");
  Serial.println(LDRValue);

  int checkDht11Value = DHT11.read(DHT11PIN);
  Serial.print("Humidity (%): "); 
  Serial.println((float)DHT11.humidity, 2);
  Serial.print("Temperature (C): "); 
  Serial.println((float)DHT11.temperature, 2);

  if (currentMode == MANUAL) {
    Serial.println("MANUAL MODE"); 
    // In manual mode, do nothing to the servo in the loop
  } else {
    Serial.println("AUTOMATIC MODE"); 
    if (LDRValue >= 3000) {  // Dark condition
      Serial.println("Dark detected, opening curtain");
      openCurtain();
    } else if (LDRValue < 3000) {  // Light condition
      Serial.println("Light detected, closing curtain");
      closeCurtain();
    } else {
      Serial.println("Invalid LDR value");
    }
  }

  delay(1000); // Add a delay to make the output readable
}

void openCurtain() {
  Serial.println("Opening Curtain...");
  servo.write(0);
  unsigned long startTime = millis();
  while (millis() - startTime < 2000) { // Adjust this delay to ensure the curtain fully opens
    switchValue1 = digitalRead(limitSwitch1);
    switchValue2 = digitalRead(limitSwitch2);
    if (switchValue1 == LOW || switchValue2 == LOW) {
      Serial.println("Limit switch pressed, stopping curtain");
      break;
    }
  }
  servo.write(90);
}

void closeCurtain() {
  Serial.println("Closing Curtain...");
  servo.write(180);
  unsigned long startTime = millis();
  while (millis() - startTime < 2000) { // Adjust this delay to ensure the curtain fully closes
    distance = getDistance();
    Serial.print("Distance: ");
    Serial.println(distance);
    if (distance < 14) { // if distance is under 14 it will stop the servo
      Serial.println("Stop Servo, object detected");
      break;
    }
  }
  servo.write(90);
}

// HC-SR04 Ultrasonic Distance Sensor Function
int getDistance() {
  //sends out a trigger sound
  digitalWrite(trigPin, LOW);
  delayMicroseconds(10);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  //returns the received echo in centimeter
  return pulseIn(echoPin, HIGH) * 0.034 / 2;
}