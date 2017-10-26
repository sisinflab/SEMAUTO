# SEMAUTO
A Java framework to build semantics-aware autoencoder neural networks from a knowledge-graph.


SEMAUTO allows the user to exploit the semantic information encoded in a
knowledge graph to build connections between units in the hidden layers of a Neural
Network, thus mimicking the topology of the knowledge graph. As a resutl we have a Neural Network which is not fully-connected and, at the same time, we have an explicit semantics (that of the knowledge graph) attached to the nodes of the hidden layer. This also leads to an explicit identification of the semantics of the "latent" factors represented by the nodes of the hidden layers.

SEMAUTO has been orignally conceived to build knowledge-aware recommender systems exploiting the information available in Linked Data datasets such as DBpedia. Hence, it exposes methods to model a user profile based on a model trained via user ratings and then it computes recommendations.  

## Quickstart

### Configuration

Edit props.txt and write the desired chains of properties to fetch from the knolwedge graph.

Edit config.properties to setup the neural network configuration.

### Item model generation

Item model files are representations of items content-based description and are used to generate recommendations.
SEMAUTO generates those files with the following settings in config file:
- computeWeights=false
- computeRecommendations=false
- mergeRecommendations=false
Item model files are stored within the specified directory in config file.

#### Run
```bash
java -Xms24g -Xmx48g -jar semauto.jar
```

### Building User Profiles

User profiles are generated and stored within the specified directory in config file. Each file named as the user id represents a user profile. It contains all the features and their weight for a certain user.
In order to generate user profiles, the related property should be enabled in config file:
- computeWeights=true

#### Run
```bash
java -Xms24g -Xmx48g -jar semauto.jar
```

## How to cite
Please cite SEMAUTO if it helps your research. You can use the following BibTeX entry:
```
@inproceedings{Bellini:2017:AUR:3125486.3125496,
 author = {Bellini, Vito and Anelli, Vito Walter and Di Noia, Tommaso and Di Sciascio, Eugenio},
 title = {Auto-Encoding User Ratings via Knowledge Graphs in Recommendation Scenarios},
 booktitle = {Proceedings of the 2nd Workshop on Deep Learning for Recommender Systems},
 series = {DLRS 2017},
 year = {2017},
 isbn = {978-1-4503-5353-3},
 location = {Como, Italy},
 pages = {60--66},
 numpages = {7},
 url = {http://doi.acm.org/10.1145/3125486.3125496},
 doi = {10.1145/3125486.3125496},
 acmid = {3125496},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Autoencoders, DBpedia, Deep Learning, Knowledge graphs, Linked Open Data, Recommender Systems},
} 
```

Contacts
-------

   Tommaso Di Noia, tommaso [dot] dinoia [at] poliba [dot] it  
   
   Vito Bellini, vito [dot] bellini [at] poliba [dot] it 
   
   Angelo Schiavone, a [dot] schiavone5 [at] studenti [dot] poliba [dot] it  
