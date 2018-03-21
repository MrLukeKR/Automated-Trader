package Portfolio;

public class Genome {
    double[] genes;
    Double fitness;

    public Genome(double[] genes){ this.genes = genes.clone(); }

    public void setGene(int index, double gene){genes[index] = gene; }
    public void setGenes(double[] genes){this.genes = genes.clone();}
    public void setFitness(double fitness){this.fitness = fitness;}

    public double[] getGenes(){return genes;}
    public double getGene(int index){return genes[index];}
    public Double getFitness() {return fitness;}

    public boolean containsNaN(){
        for (Double gene : genes)
            if(Double.isNaN(gene))
                return true;

        return false;
    }
}
