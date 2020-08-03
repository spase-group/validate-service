#!/bin/bash 

# Compile java classes that support the validation service.
# Place compiled classes in WEB-INF/classes folder. 
# Supporting jar files are expected to be in WEB-INF/lib

# Create "WEB-INF/classes" folder - it must exist for compile to work
mkdir -p site/WEB-INF/classes

# Compile code
echo ""
echo "******************"
echo "Note: There will be a warning about 'MalformedByteSequenceException'"
echo "      While this a 'proprietary' feature of the Apache Xerces jar,"
echo "      we use it as part of the validation."
echo "******************"
echo ""
javac -d site/WEB-INF/classes -extdirs site/WEB-INF/lib src/org/spase/web/Validator.java