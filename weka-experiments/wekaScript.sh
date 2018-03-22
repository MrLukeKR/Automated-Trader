#!/bin/bash

file=$1;
learningRates=(0.1 0.3)
hiddenLayers=(1 3)
files="$(find TrainingFiles/ -type f)"

	echo "Converting to .ARFF file"
	header="$(head -n 1 $file)"
	cleanFile="${file%.*}.csv1"
	echo "$file"
	echo "$header" > "$cleanFile"
	sed "/$header/d" "$file" >> "$cleanFile"

	arffFile="${file%.*}.arff"

	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.core.converters.CSVLoader $cleanFile > $arffFile	

echo "$arffFile"

echo "Converting Numeric to Nominal (Regression to Classification)"
	
jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.filters.unsupervised.attribute.NumericToNominal -i $arffFile -o "${arffFile}1" -R last

	rm $cleanFile
	mv "${arffFile}1" $arffFile
	
	echo "Logistic Regression for $arffFile"
	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.functions.Logistic -t $arffFile -split-percentage 70 -R 1.0E-8 -num-decimal-places 4
	
	echo "Random Forest for $arffFile"	
	
	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.trees.RandomForest -P 100 -I 100 -num-slots 1 -K 0 -M 1.0 -S 1 -t $arffFile -split-percentage 70
	

	echo MultiLayer Perceptron for $arffFile
	for lr in "${learningRates[@]}" 
	do
		for hl in "${hiddenLayers[@]}" 
		do
			echo "Multilayer Perceptron (Hidden Layers: $hl, Learning Rate: $lr) for $arffFile"	
			jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.functions.MultilayerPerceptron -L $lr -H $hl -N 500 -V 0 -S 0 -E 20 -t $arffFile -split-percentage 70
		done
	done

	echo "Support Vector Machine (Polynomial Kernel) for $arffFile"

	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.functions.SMO -C 1.0 -L 0.001 -P 1.0E-12 -N 0 -V -1 -W 1 -K "weka.classifiers.functions.supportVector.PolyKernel -E 1.0 -C 250007" -calibrator "weka.classifiers.functions.Logistic -R 1.0E-8 -M -1 -num-decimal-places 4" -t $arffFile -split-percentage 70

	echo "Support Vector Machine (Normalised Polynomial Kernel) for $arffFile"

	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.functions.SMO -C 1.0 -L 0.001 -P 1.0E-12 -N 0 -V -1 -W 1 -K "weka.classifiers.functions.supportVector.NormalizedPolyKernel -E 1.0 -C 250007" -calibrator "weka.classifiers.functions.Logistic -R 1.0E-8 -M -1 -num-decimal-places 4" -t $arffFile -split-percentage 70

	echo "Support Vector Machine (Radial Basis Function Kernel) for $arffFile"

	jdk1.8.0_161/bin/java -cp weka-3-8-2/weka.jar weka.Run weka.classifiers.functions.SMO -C 1.0 -L 0.001 -P 1.0E-12 -N 0 -V -1 -W 1 -K "weka.classifiers.functions.supportVector.PolyKernel -E 1.0 -C 250007" -calibrator "weka.classifiers.functions.RBFKernel -R 1.0E-8 -M -1 -num-decimal-places 4" -t $arffFile -split-percentage 70

	rm $arffFile
