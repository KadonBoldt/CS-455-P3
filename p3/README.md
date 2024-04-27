# Project 2: Identity Server (Phase I)

* Author: Kadon Boldt & Jacob Seppa
* Class: CS455 [Distributed Systems] Section #1

## Overview

The program is split into two parts consisting of the client side and
server side applications. The server side utilizes RMI to create a server
that connects to a REDIS database that stores ID account information. The
server allows connections from clients to manage the data in the databases.
The client side parses command line arguments and serves as the UI to send
requests to the server in order to manage data. Both the client and server
use SSL communications.

## Manifest

```
- p2 // Top level directory for the project.
----- client // Package for client side program.
--------- IdClient.java // Main file for the client-side program.
----- libs // Library directory used for REDIS.
--------- commons-pool2-2.12.0.jar // REDIS dependency.
--------- jedis-5.1.2.jar // REDIS dependency.
--------- slf4j-api-2.0.12.jar // REDIS dependency.
--------- slf4j-simple-2.0.12.jar // REDIS dependency.
----- resources // Stores security resources used for SSL communication.
--------- Client_Trustore // Trust store used by the client for SSL.
--------- mysecurity.policy // Policy to give java proper permissions for SSL.
--------- Server.cer // Certificate of trust for SSL.
--------- Server_Keystore // Key store used by the server for SSL.
----- server // Package for server side program.
--------- IdServer.java // Main file for the server-side program.
--------- Server.java // RMI interface for IdServer.
```

## Building the project

All required libraries are included in the git repository (not ideal, I know, but it was easier this way).

To compile the project, run the Makefile using the following command in the top level directory:

`make`

Tricky, I know, but hopefully you were able to do it. To run the program (on a Linux machine), use the
following much simpler command, still in the top level directory:

`java .:libs/* server/IdServer [--numport/-n <port#>] [--verbose/-v]` for the server

`java client/IdClient --server <serverhost> [--numport <port#>] <query>` for the client

Here is a list of queries you can run:

```
--lookup/-l <loginname>
--reverse-lookup/-r <UUID>
--modify/-m <oldloginname> <newloginname> [--password/-p <password>]
--delete/-d <loginname> [--password/-p <password>]
--get/-g users|uuids|all"""
```

## Features and usage

Here is the usage statement for the client:

```
Usage:      IdClient --server/-s <serverhost> [--numport/-n <port#>] <query>
Queries:    --create/-c <loginname> [<real name>] [--password/-p <password>]
            --lookup/-l <loginname>
            --reverse-lookup/-r <UUID>
            --modify/-m <oldloginname> <newloginname> [--password/-p <password>]
            --delete/-d <loginname> [--password/-p <password>]
            --get/-g users|uuids|all"""
```

-c allows you to create a new login with the option of attaching a password for extra
security. -l allows you lookup accounts using a login name. -r allows you to do the same,
but using a UUID instead. -m allows you to change the login name of an existing account,
tho some may require a password to modify. -d allows an account to be deleted, pending
password approval in certain cases. -g allows the user to obtain all information other
than passwords stored for all accounts.

Here is the usage statement for the server:

```
Usage: IdServer [--numport/-n <port#>] [--verbose/-v]

```

-v allows you to see detailed output from the server as it receives requests.

## Testing

In order to test this program, thorough manual unit tests were performed. The command line arguments
were tested for several number of different types of arguments and different inputs. I couldn't find
a way to break it, but maybe that's just because subconsciously I didn't want to. On top of that,
each different function was tested several times over and with different logins each time. Functions 
with an optional password were tested both with and without a password. Not only was expected output
tested for, but I tested for proper error cases. I wasn't able to find a place where the errors wouldn't
occur when necessary, but I'm sure if you tried hard enough there would be a point where everything 
collapses in on itself in spectacular fashion. Anyways, all that was for the client. For the server,
I just typed -v in the command line once, entered in a couple requests through the client, and said
"looks good enough". I figured that was sufficient considering the functionality is mostly seen on
the client side.

The difference between unit testing and integration testing is that while unit tests aim to sort of
put certain features in a testing vacuum and isolate them to determine their effectiveness and accuracy,
integration tests are used to see how well a feature works with the rest of the program. In other words,
integration tests test the program as a whole as new features are added to the existing project.

### Known Bugs

None that I know of, and boy I sure hope there's none I don't know of.

## Reflection

Well, I started way later than I should've (par for the course in the second half of my academic career) and
I finished way closer to the deadline than I had any right to. That being said, this time I have the excuse
that my coding partner went on sabbatical and therefor I was left to take on this behemoth of a project as
a one man programming machine. I'd say the results are pretty good given the circumstances. Am I confident
that this code will be something I'll be proud of in a few years time? Definitely not, I'll probably end up
hiding it from my close relatives and live a scandalous life if I ever look back at it. But am I proud of it
in the moment? Absolutely. In fact, if not for the 3-hour SSL bug that ended up being a wrong directory in one
line of code, I'd say this was a really fun project. Well, I suppose it's time to ignore the lesson I learned
this time around and get an early start in on procrastinating the next project. I hope it is less stressful but
just as fun as this one turned out to be.

## Video showcase

It's coming, I promise.

## Sources used

The amount of resources outside of lectures I looked at was substantial.

The amount of resources I actually used was nothing, because it turns out all
my problems were easy to solve if only I knew how to read my own code.
