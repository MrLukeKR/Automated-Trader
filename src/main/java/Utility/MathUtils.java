package Utility;

public class MathUtils {
    static public double[] dot(double[] vec, double[][] matrix, boolean vecIsColumn){
        double[] result = new double[matrix[0].length];

        for(int i = 0; i < matrix[0].length; i++){
            result[i] = 0;
            for(int j = 0; j < matrix[0].length; j++)
                if(vecIsColumn)
                    result[i] += vec[j] * matrix[j][i];
                else
                    result[i] += vec[j] * matrix[i][j];
        }

        return result;
    }

    static public double dot(double[] vec1, double[] vec2){
        double result = 0;
        for(int i = 0; i < vec1.length; i++)
            result += vec1[i] * vec2[i];

        return result;
    }
}
