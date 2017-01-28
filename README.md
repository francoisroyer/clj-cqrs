# clj-cqrs

A Clojure library implementing CQRS and event-sourcing using Immutant components.

## Usage

TODO show how to to use as a template

## Deploy in a Wildfly cluster in Openshift

rhc create-app $APPNAME wildfly --from-code $GIT_LOCATION

rhc create-app wf wildfly --no-git

lein do clean, immutant

cp .war to ROOT.war then scp to deployments + add ROOT.war.dodeploy
/var/lib/openshift/{APP_ID}/app-root/runtime/dependencies/jbossews/webapps
/var/lib/openshift/5781680e2d52716c2100006a

scp <your local file name> <your hash number>@<your app name>-<your domain name>.rhcloud.com:~/<app name>/data/

scp target/clj-cqrs.war 5781680e2d52716c2100006a@wf-datasio.rhcloud.com:~/app-deployments/2016-07-09_17-09-43.431/repo/deployments/

cp app-deployments/2016-07-09_17-09-43.431/repo/deployments/clj-cqrs.* wildfly/standalone/deployments/

visit http://wf-datasio.rhcloud.com/clj-cqrs/api-docs/index.html

http://dmitrygusev.blogspot.fr/2013/06/deploy-application-binaries-war-to.html
http://jagadesh4java.blogspot.fr/2014/04/deploy-custom-application-into-openshift.html

TODO cmd line with set of questions to build commands + memoize


APP Assisted learning -> document import/upload + BPM/workflow + screens to advance state

Index -> category trees, documents
Connectors + extractor for entities etc...
Datasets - url
Workflows - BPM spec?
Workbooks/Cases/study/investigation
Dataflow - realtime (push or pull) or batch queries


## Docker services

#Getting started with Keycloak: http://blog.keycloak.org/2015/10/getting-started-with-keycloak.html
docker run jboss/keycloak --name keycloak
docker inspect --format='.NetworkSettings.IPAddress' keycloak
Or: 
docker run jboss/keycloak --name keycloak -p 8080:8080

#https://wesmorgan.svbtle.com/deploying-clojure-apps-with-docker-and-immutant-2

## References
http://www.multunus.com/blog/2016/02/noobs-walkthrough-re-frame-app/
https://lostechies.com/jimmybogard/2016/06/01/cqrs-and-rest-the-perfect-match/

ookami86 Event Sourcing in practice
Trigger side effects only when applying Command to object
Then Events applied to change state (this step only called on replay - not the other one)

## License

Copyright © 2016 Francois Royer - Datasio - froyer@datasio.com

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
