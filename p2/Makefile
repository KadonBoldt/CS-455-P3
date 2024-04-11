#---------------------------------------------------------------
# Using this Makefile
#
#	To compile the java source (and generate documentation)
#
#	make
#
#	To clean up your directory
#
#	make clean
#
#	Author's note: this Makefile was blatantly stolen from the
#	example programs because I somehow never learned how to
#	write Makefiles yet and at this point I'm too scared to ask.
#
#---------------------------------------------------------------

JFLAGS= -cp .:libs/*

# Recognize files with .class and .java extensions
.SUFFIXES: .class .java

# This is a rule to convert a file with .java extension
# into a file with a .class extension. The macro $< just
# supplies the name of the file (without the extension)
# that invoked this rule.

.java.class:
	javac $(JFLAGS) $<

# To satisfy the rule named compile, we must have the following
# class files (with date no later than the source .java files).
# We also must have satisfied the rule named doc.

all: compile

compile: compile-server compile-client

compile-server: server/IdServer.class server/Server.class

compile-client: client/IdClient.class

clean:
		rm --force server/*.class client/*.class