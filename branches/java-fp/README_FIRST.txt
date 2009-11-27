The SVN repository does not include library jars. Before
you can build anything or even get ant to run succesfully
you'll need to fetch these. The easiest way is to run

ant -f contrib.xml

Then, everything can be built with 

ant

You'll want to start off by building every subproject, because
this will also make sure each subproject gets its dependencies
built and copied onto the classpath.

To run the Syxaw automated test

cd syxaw
ant verifyall

(Metadata-only will currently fail). The test creates two local Syxaw
instances and executes synchronization commands.

A good way to access the Syxaw command line and experiment with
instances is to start the "Syxaw runner" GUI with

ant syxrunner

