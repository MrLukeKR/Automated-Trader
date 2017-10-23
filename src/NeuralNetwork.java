import java.util.ArrayList;

class NeuralNetworkNode{
    double activation, delta;
    ArrayList<NeuralNetworkPath> previous = new ArrayList<>();
    ArrayList<NeuralNetworkPath> next = new ArrayList<>();

    NeuralNetworkNode(){ activation = Math.random(); }
}

class NeuralNetworkPath{
    double weight;
    NeuralNetworkNode from, to;

    NeuralNetworkPath(NeuralNetworkNode from, NeuralNetworkNode to){
        this.from=from;
        this.to=to;
    }
}

class Layer{
    ArrayList<NeuralNetworkNode> nodes = new ArrayList<>();

    public void addNode(NeuralNetworkNode node){nodes.add(node);}
    public NeuralNetworkNode getNode(int index){ return nodes.get(index); }
}

public class NeuralNetwork {
    private final double LEARNING_RATE = 0.1;
    Layer inputLayer = new Layer();
    ArrayList<Layer> hiddenLayers = new ArrayList<>();
    Layer outputLayer = new Layer();

    public void init(int hiddenLayers, int inputNodes, int hiddenNodes, int outputNodes){
        for(int i = 0; i < inputNodes; i++)
            inputLayer.addNode(new NeuralNetworkNode());

        for(int i = 0; i < hiddenLayers; i++) {
            this.hiddenLayers.add(new Layer());
            for (int j = 0; j < hiddenNodes; j++)
                this.hiddenLayers.get(i).addNode(new NeuralNetworkNode());
        }

        for(int i = 0; i < outputNodes; i++)
            outputLayer.addNode(new NeuralNetworkNode());

        for(int i = 0; i < inputNodes; i++){
            for(int j = 0; j < hiddenNodes; j++) {
                NeuralNetworkPath temp = new NeuralNetworkPath(inputLayer.getNode(i), this.hiddenLayers.get(0).getNode(j));
                inputLayer.getNode(i).next.add(temp);
                this.hiddenLayers.get(0).getNode(j).previous.add(temp);
            }
        }

        for(int i = 0; i < hiddenLayers - 1; i++){
            for(int j = 0; j < hiddenNodes; j++) {
                for(int k = 0; k < hiddenNodes; k++) {
                    NeuralNetworkPath temp = new NeuralNetworkPath(this.hiddenLayers.get(i).getNode(j), this.hiddenLayers.get(i+1).getNode(k));
                    this.hiddenLayers.get(i).getNode(j).next.add(temp);
                    this.hiddenLayers.get(i+1).getNode(k).previous.add(temp);
                }
            }
        }

        for(int i = 0; i < hiddenNodes; i++){
            for(int j = 0; j < outputNodes; j++) {
                NeuralNetworkPath temp = new NeuralNetworkPath(this.hiddenLayers.get(hiddenLayers-1).getNode(i), outputLayer.getNode(j));
                this.hiddenLayers.get(hiddenLayers-1).getNode(i).next.add(temp);
                outputLayer.getNode(j).previous.add(temp);
            }
        }
    }

    public void backprop(){

    }

    public void train(ArrayList<Double[]> input, ArrayList<Double[]> output){

    }
}
