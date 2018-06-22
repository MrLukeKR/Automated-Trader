#!/bin/bash

echo "----------------------------------------------------------------------"
echo "WEKA Dataset Testing Kit for Anaylsis of Automated Trading ML Features"
echo "                   Written by Luke Kevin Rose 2018                    "
echo "----------------------------------------------------------------------"
echo "Starting WEKA Script..."

files="$(find TrainingFiles/ -type f)"

for file in $files; 
do
	filename="$(basename $file)";
	output="${filename%.*}"
	if [ ! -f "Results/${output}_RESULTS.txt" ]; then
	./wekaScript.sh "$file" > "Results/${output}_RESULTS.txt"
	fi
done

wait

echo "FINISHED ALL WEKA SCRIPTS!"
