#!/usr/bin/env bash

echo "#!/bin/sh" > .git/hooks/post-merge
echo "java -jar InstanceSync.jar" >> .git/hooks/post-merge

echo "Done setting up hooks"

# For possible arguments, read https://github.com/WFPhantom/PrismInstanceSync#args
java -jar InstanceSync.jar

read -n 1 -s -r -p "Press any key to continue..."
echo