# End an instance of a SPASE website. 
#
# Stops the running container and removes the container instance.
#
# Run in folder with Dockerfile.
#
# Command line options:
# $1: name for container (default: spase-website)

name=${1:-spase-validate}

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

# Stop container, then remove instance
$SUDO docker stop $name
$SUDO docker rm $name
