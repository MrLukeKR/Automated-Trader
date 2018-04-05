package Portfolio;

import java.util.Map;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.4
 */

class Genome {
    private Map<String, Double> genes;
    private Double fitness;

    /**
     * Initialises the Genome with an array of gene values
     *
     * @param genes Map of asset weight/stock pair values
     */
    Genome(Map<String, Double> genes) {
        this.genes = genes;
    }

    /**
     * Replaces a gene (asset weight) with a given value
     *
     * @param stock Stock ticker to replace the weight of
     * @param gene  Value of gene to use as replacement
     */
    void setGene(String stock, double gene) {
        genes.put(stock, gene);
    }

    /**
     * Access the asset weighting array
     *
     * @return Gene array, representing the weights of assets in a portfolio
     */
    Map<String, Double> getGenes() {
        return genes;
    }

    /**
     * Access a single gene value from the gene array
     *
     * @param stock Stock ticker of the weight to retrieve
     * @return An asset weight (gene)
     */
    double getGene(String stock) {
        return genes.get(stock);
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
