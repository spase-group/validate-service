# Create a Jetty image loaded with the validator service
#
# Expose /var/lib/jetty/logs to collect logs
# Expose /var/lib/jetty/webapps/ROOT for web site
#

FROM jetty:9.4

# Web port
EXPOSE 8080

# Copy the web app
ADD site /var/lib/jetty/webapps/ROOT

# Allow external sources for log and metadata
VOLUME /var/lib/jetty/logs
VOLUME /var/lib/jetty/webapps

ENV JAVA_OPTS "-Djdk.tls.trustNameService=true"
