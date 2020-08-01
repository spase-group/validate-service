# SPASE Metadata Validator
Stand along SPASE metadata validator.

Code to run the JAVA based validation service in a Java application server.

# Scripts

<b>docker-build.sh</a>: Build (or rebuild) a docker image with the validate service.

<b>docker-up.sh</a>: Run a container with the validate service image.

<b>docker-down.sh</a>: Stop the validate service container and remove the container.

# Docker Details

## Exposed ports
	8080 : Web server
 
## Volumes
	website: var/lib/jetty/webapps/ROOT
	
## Create Image
```
docker build -t spase-validate .
```

## Run Container
- Use option "-p" to map to host port.
- mount web site content at /var/lib/jetty/webapps/ROOT
- mount logs at /var/lib/jetty/logs
```
name=spase-website
docker run -p $port:8080 -d --name $name --restart unless-stopped  \
   --mount type=bind,src=$(pwd)/../site,dst=/var/lib/jetty/webapps/ROOT \
   $name
```
