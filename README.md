# Overview

This project contains re-usable code that simplifies a variety of Hazelcast-related tasks.

# Contributor Guidelines

Documentation is the difference between potentially re-usable code and code that is actually re-used.

> Please be sure everything in this repository is thoroughly documented!

## For Java
1. All packages *should* begin with 
`hazelcast.platform`.  Package names *should not* start with `com.hazelcast` since that code 
would not be deployable on Viridian.  
1. Note that anything that connects to Viridian must use the Enterprise release as Viridian
requires TLS.

