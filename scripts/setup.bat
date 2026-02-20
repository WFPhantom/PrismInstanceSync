@echo off

type NUL > .git/hooks/post-merge
echo #!/bin/sh > .git/hooks/post-merge
echo java -jar InstanceSync.jar >> .git/hooks/post-merge

echo Done setting up hooks
:: For possible arguments, read https://github.com/WFPhantom/PrismInstanceSync#args
java -jar InstanceSync.jar

pause