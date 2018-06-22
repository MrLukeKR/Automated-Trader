package Portfolio;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.4
 */

class Genome {
    private Map<String, Double> genes;
    private double fitness = Double.NaN;

    /**
     * Initialises the Genome with an array of gene values
     *
     * @param genes Map of asset weight/stock pair values
     */
    Genome(Map<String, Double> genes) {
        this.genes = genes;
    }

    /**
     * Copy constructor to copy a genome
     *
     * @param genome Genome to copy the values of
     */
    Genome(Genome genome) {
        genes = new TreeMap<>();
        for (String stock : genome.getGenes().keySet()) genes.put(stock, genome.getGene(stock));
        fitness = genome.getFitness();
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
     * Replaces a set of genes (asset weights) with a given list of values
     *
     * @param genes Map of stock-to-weight values
     */
    void setGenes(Map<String, Double> genes) {
        for (String stock : genes.keySet()) this.genes.put(stock, genes.get(stock));
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
        this.fitness = fitness; }
}
