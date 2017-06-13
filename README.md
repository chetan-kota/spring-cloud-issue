# spring-cloud-issue

1. configuration-client is a spring cloud config client module
2. configuration-service is a spring cloud config server module
3. server is a module contain jettyStarter, which is used to deploy the war files
4. folder "dist" contains the dependencies libs and war files

to start the jetty server, run the command line from the current folder

java -server -Dfile.encoding=UTF-8 -cp "./dist/lib/*" hello.JettyStarter ${path}

where ${path} is the current folder path, from where the JetterStarter.java can find the war files with ${path} + "/dist/war/"
