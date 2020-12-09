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
#javac -d site/WEB-INF/classes -extdirs site/WEB-INF/lib src/org/spase/web/Validator.java
# javac 11 is a pain 20201022cfd
javac -d site/WEB-INF/classes -cp "site/WEB-INF/lib/commons-cli-1.1.jar:site/WEB-INF/lib/cos.jar:site/WEB-INF/lib/igpp-util-1.0.18.jar:site/WEB-INF/lib/igpp-xml-1.0.5.jar:site/WEB-INF/lib/uploadbean.jar:site/WEB-INF/lib/commons-net-1.4.1.jar:site/WEB-INF/lib/igpp-servlet-1.0.3.jar:site/WEB-INF/lib/igpp-web-1.0.9.jar:site/WEB-INF/lib/javax.servlet.jar" src/org/spase/web/Validator.java
