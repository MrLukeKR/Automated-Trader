package Portfolio;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.4
 */

class Genome {
    private double[] genes;
    private Double fitness;

    /**
     * Initialises the Genome with an array of gene values
     *
     * @param genes Double array of asset weight values
     */
    Genome(double[] genes) {
        this.genes = genes.clone();
    }

    /**
     * Replaces a gene (asset weight) with a given value
     *
     * @param index Index of gene to replace
     * @param gene  Value of gene to use as replacement
     */
    void setGene(int index, double gene) {
        genes[index] = gene;
    }

    /**
     * Access the asset weighting array
     *
     * @return Gene array, representing the weights of assets in a portfolio
     */
    double[] getGenes() {
        return genes;
    }

    /**
     * Access a single gene value from the gene array
     *
     * @param index Index of the gene to retrieve the value from
     * @return An asset weight (gene)
     */
    double getGene(int index) {
        return genes[index];
    }

    /**
     * Access the fitness of this Genome
     *
     * @return The performance measurement of the Genome
     */
    Double getFitness() {
        return fitness;
    }

    /**
     * Sets the performance measure of this Genome
     *
     * @param fitness Performance measurement value
     */
    void setFitness(double fitness) {
        this.fitness = fitness;
    }
}
