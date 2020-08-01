# Run an instance of a web service. 
#
# Run in folder with Dockerfile.
#
# Command line options:
# $1: port number for container (default: 8080)
# $2: name for container (default: spase-website)
# $3: path to folder for website (default: $(pwd)/../site)
# $4: path to folder for logs (default: $(pwd)/../logs)

port=${1:-8080}
name=${2:-spase-validate}
website=${3:-$(pwd)/../site}
logs=${4:-$(pwd)/../logs}

# Set SUDO appropriate for OS
SUDO=""
case "$OSTYPE" in
  solaris*) SUDO="sudo" ;;
  darwin*)  SUDO="sudo" ;; 
  linux*)   SUDO="sudo" ;;
  bsd*)     SUDO="sudo" ;;
  msys*)    SUDO="" ;;
  *)        SUDO="" ;;
esac

#
# Build the image if it doesn't exist
#
$SUDO docker inspect "$name" > /dev/null 2>&1 || $SUDO docker build -t $name .

# Run container
# Use option "-p" to set host port map.
$SUDO docker run -p $port:8080 -d --name $name --restart unless-stopped $name