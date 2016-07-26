package dp.classification;

import java.util.HashMap;
import java.util.List;
import cz.zcu.fav.liks.ml.features.Feature;
import cz.zcu.fav.liks.ml.features.FeatureVectorGenerator;
import cz.zcu.fav.liks.ml.lists.ListIterator;
import cz.zcu.fav.liks.ml.lists.TrainingInstanceList;
public class WordFeature implements Feature<List<String>> {


		private static final long serialVersionUID = 84002172988943223L;
		private HashMap<String, Integer> wordMap;	// seznam slov v dokumentech
		private int threshold;		// minimální počet výskytů slova pro zařazení
		
		/** Konstruktor
		 * @param threshold - minimální počet výskytů slova pro zařazení
		 */
		public WordFeature(int threshold){
			this.threshold = threshold;
		}
		
		/* (non-Javadoc)
		 * @see cz.zcu.fav.liks.ml.features.Feature#getNumberOfFeatures()
		 */
		@Override
		public int getNumberOfFeatures() {
			return wordMap.size() + 1;
		}

		/* (non-Javadoc)
		 * @see cz.zcu.fav.liks.ml.features.Feature#extractFeature(cz.zcu.fav.liks.ml.lists.ListIterator, cz.zcu.fav.liks.ml.features.FeatureVectorGenerator)
		 */
		@Override
		public void extractFeature(ListIterator<List<String>> it, FeatureVectorGenerator generator) {
			List<String> words = it.getRelative(0);
			for(String word : words){
				Integer index = wordMap.get(word);
				if(index == null){
					generator.setFeature(wordMap.size(), 1.0);
				} else {
					generator.setFeature(index, 1.0);
				}
			}
		}

		/* (non-Javadoc)
		 * @see cz.zcu.fav.liks.ml.features.Feature#train(cz.zcu.fav.liks.ml.lists.TrainingInstanceList)
		 */
		@Override
		public void train(TrainingInstanceList<List<String>> instances) {
			HashMap<String, Integer> counts = new HashMap<String, Integer>();
			
			ListIterator<List<String>> iterator = instances.iterator();
			while(iterator.hasNext()){
				List<String> words = iterator.next();
				for(String word : words){
					Integer count = counts.get(word);
					if(count == null){
						counts.put(word, 1);
					} else {
						counts.put(word, count + 1);
					}
				}
			}
			
			wordMap = new HashMap<String, Integer>();
			int index = 0;
			for(String word : counts.keySet()){
				int count = counts.get(word);
				if(count > threshold){
					wordMap.put(word, index);
					index++;
				}
			}
			
		}


	

}
