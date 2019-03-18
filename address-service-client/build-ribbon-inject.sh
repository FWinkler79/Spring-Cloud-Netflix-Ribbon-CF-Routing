#!/bin/bash

echo "Executing Maven Build"
echo "- deactivating profile: ribbon-cf"
echo "- activating profile:   ribbon-inject"

# disables the ribbon-cf profile and enables the ribbon-inject profile
# essentially, this switches the main class used inside the generated .jar file.
mvn clean package -P -ribbon-cf,ribbon-inject