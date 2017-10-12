# SEMAUTO
A Java framework to build semantics-aware autoencoder neural network from a knowledge-graph.

## Quickstart

### Configuration

Edit props.txt and write the desired chains of properties to fetch from the graph.

Edit config.properties to setup the neural network configuration.

Item model files are representations of items content-based description and are used to generate recommendations.
SEMAUTO generates those files with the following settings in config file:
- computeWeights=false
- computeRecommendations=false
- mergeRecommendations=false
Item model files are stored within the specified directory in config file.

### Run
```bash
java -Xms24g -Xmx48g -jar semauto.jar
```

### User Profiles

User profiles are generated and stored within the specified directory in config file. Each file named as the user id represents a user profile. It contains all the features and their weight for a certain user.

## How to cite
Please cite SEMAUTO if it helps your research. You can use the following BibTeX entry:
```
@inproceedings{Bellini:2017:AUR:3125486.3125496,
 author = {Bellini, Vito and Anelli, Vito Walter and Di Noia, Tommaso and Di Sciascio, Eugenio},
 title = {Auto-Encoding User Ratings via Knowledge Graphs in Recommendation Scenarios},
 booktitle = {Proceedings of the 2Nd Workshop on Deep Learning for Recommender Systems},
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
