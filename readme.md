# OData JPA Extension
This project contains a JPA Datasource and Query extension for the SDL OData framework. With this extension you can take you existing JPA entity model and expose it in the OData framework (https://github.com/sdl/odata).

For the moment the extension is powered by JPA annotated entity model and the persistence engine is backed by Hibernate. In the future we might abstract away the persistence engine to be replaceable by something else.

## Configuration
In order to configure the extension it needs a few properties, see the below example for an in memory HSQLDB.
```
datasource.url=jdbc:hsqldb:mem:testdb
datasource.username=sa
datasource.password=
datasource.driver=org.hsqldb.jdbc.JDBCDriver
datasource.validationQuery=select 1
datasource.dialect=org.hibernate.dialect.HSQLDialect
datasource.entitymodel=com.sdl.odata.jpa.model
datasource.odatanamespace=Sdl.Model
datasource.generateDDL=true
```

You can easily replace the above values with any other Hibernate / JPA supported database.

The properties indicate the package of the JPA annotated model, please adjust this to your own domain model and ensure its loaded on the classpath.

## Starting the example
In the odata-jpa-test module there is an example project to demonstrate the JPA extension. This example already has a pre-defined controller and container and is directly able to start using spring-boot.

In order to start the application you can use the following command:
```
mvn -f odata-jpa-test/pom.xml spring-boot:run
```

The example contains two entities namely 'User' and 'PhotoItem', these are available in the package 'com.sdl.odata.jpa.model'. These will result in an OData Collection named 'Users' and 'PhotoItems'.

### Inserting data
Let's insert some test data that can be used in example demo:
```
curl -i -X POST -d @odata-jpa-test/src/test/resources/user-sample.json http://localhost:8080/jpa.svc/Users --header "Content-Type:application/json"
```

### Querying the test data
You can query the database with a Query like this for example:
```
http://localhost:8080/jpa.svc/Users?$filter=name eq 'Donald'
```
### Deleting data
To remove the entity you can use the following command:
```
curl -i -X DELETE http://localhost:8080/jpa.svc/Users\(\'Donald\'\) --header "Content-Type:application/json"
```
