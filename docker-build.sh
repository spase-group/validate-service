# Remove an instance of image and then build the image according to Dockerfile.
#
# Command line options
# $1: name for container (default: spase-validate)

name=${1:-spase-validate}

# Remove container, stop container if running
docker rm --force $name

# Remove image
docker rmi $name

#
# Build the image "spase-xmleditor" if it doesn't exist
#
docker inspect "$name" > /dev/null 2>&1 || docker build -t $name .

