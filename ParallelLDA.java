



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import ldalm.LDAInferencer;
import ldalm.NoStemmer;
import ldalm.TopicInterface;
import ldalm.utils.MalletFormatDataReader;
import ldalm.utils.StemmerPipe;
import ldalm.utils.TokenizerPipe;
import lm.enums.Mapping;
import lm.enums.MappingTypeEnum;
import lm.ngram.NGramLM;
import lm.ngram.ProbabilityEstimator;
import lm.ngram.mapping.NoClassMapping;
import lm.ngram.mapping.WordToClassMapping;
import lm.ngram.smoothing.MLENGramLM;
import lm.reader.LMReader;
import lm.reader.token.Token;
import lm.reader.token.TokenSequence;

import org.apache.log4j.Logger;
import ws.stemmer.Stemmer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class ParallelLDA extends ProbabilityEstimator implements TopicInterface {

	private static final long serialVersionUID = 7922187332903409651L;
	static transient Logger log = Logger.getLogger(ParallelLDA.class);
	
	protected int NUM_ITERATION = 1000;
	protected int MIN_OCCURRENCE = 5;
	
	protected ParallelTopicModel lda;
	protected WordToClassMapping mapping1;
	protected WordToClassMapping mapping2;
	protected Set<String> vocabulary1;
	protected Set<String> vocabulary2;
	
	public String fileName = "data/train.txt";
	protected transient Stemmer stemmer;
	protected SerialPipes pipes;
	protected int NUM_TOPICS;
	
	protected transient String[] actualWords;
	protected transient int[] actualTopics;
	protected transient int[] actualTopicsHistogram;
	//protected transient Map<String, Integer> wordToTopic;
	protected transient double topicDenominator;
	protected transient float[][] topicWordProbabilities;
	
	//for OOV statistics
	protected transient int lowOccurredStems1 = 0;
	protected transient int lowOccurredStems2 = 0;
	protected transient float probabilityOfOOV1 = 0.0f;
	protected transient float probabilityOfOOV2 = 0.0f;
	protected transient float[] topicWordProbabilitySums1;
	protected transient float[] topicWordProbabilitySums2;
	
	protected String preffix1;
	protected String preffix2;
	
	public ParallelLDA(float UNSEENS_DIV, int NUM_TOPICS, String trainFileName, String preffix1, String preffix2) throws IOException, ClassNotFoundException {
		super(1, UNSEENS_DIV);
		this.NUM_TOPICS = NUM_TOPICS;
		this.fileName = trainFileName;
		this.preffix1 = preffix1;
		this.preffix2 = preffix2;
		this.stemmer = new NoStemmer();
		this.pipes = createPipes();
		log = Logger.getLogger(ParallelLDA.class);
	}
	
	public static ParallelLDA load(String fileName) throws IOException, ClassNotFoundException {
		FileInputStream fis = new FileInputStream(fileName);
	    ObjectInputStream ois = new ObjectInputStream(fis);
	    ParallelLDA ldalm = (ParallelLDA) ois.readObject();
	    fis.close();
	    
	    ldalm.setStemmer(new NoStemmer());
	    
	    for (int i=0; i<ldalm.getPipes().size(); i++) {
	    	if (ldalm.getPipes().getPipe(i) instanceof StemmerPipe) {
	    		((StemmerPipe)ldalm.getPipes().getPipe(i)).setStemmer(ldalm.getStemmer());
	    		break;
	    	}
	    }

	    if (ldalm.vocabulary1==null) ldalm.vocabulary1 = new HashSet<String>();
	    if (ldalm.vocabulary2==null) ldalm.vocabulary2 = new HashSet<String>();
	    ldalm.createMatrix();
	    ldalm.calculateOOVProbability();
	    log.info(ldalm.toString());
	    return ldalm;
	}
	
	public void save(String fileName) throws IOException {
		 FileOutputStream fos = new FileOutputStream(fileName);
		 ObjectOutputStream oos = new ObjectOutputStream(fos);
	     oos.writeObject(this);
	     fos.close();
	}
	
	private SerialPipes createPipes() {
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		//pipeList.add(new CharSequenceLowercase() );
		pipeList.add(new TokenizerPipe());
		pipeList.add(new StemmerPipe(stemmer));
		pipeList.add(new TokenSequence2FeatureSequence() );
		return new SerialPipes(pipeList);
	}
	
	private InstanceList createInstances() throws UnsupportedEncodingException, FileNotFoundException {
		InstanceList instances = new InstanceList(this.pipes);
		Reader fileReader = new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8");
		instances.addThruPipe(new CsvIterator(fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)"), 3, 2, 1)); // data, label, name fields
		return instances;
	}
	
	@Override
	public void computeProbabilities() {
		try {
			lda = new ParallelTopicModel(NUM_TOPICS, 50.0f, 0.1f);
			lda.addInstances(createInstances());
			lda.setNumThreads(8);
			lda.setNumIterations(NUM_ITERATION);
			lda.estimate();

			createMatrix();			
			createMapping();
			calculateOOVProbability();
			log.info(toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void createMapping() {
		try {
			log.info("mapping words to stems...");

			this.mapping1 = new NoClassMapping(new Mapping(MappingTypeEnum.STEM), UNSEENS_DIV);
			NGramLM lm1 = new MLENGramLM(1, mapping1, 1.0f);
			
			this.mapping2 = new NoClassMapping(new Mapping(MappingTypeEnum.STEM), UNSEENS_DIV);
			NGramLM lm2 = new MLENGramLM(1, mapping2, 1.0f);

				LMReader readerTrain = new MalletFormatDataReader(fileName, "UTF-8", false, stemmer, new HashSet<String>());
				TokenSequence sequence;
				while ((sequence = readerTrain.readNotEmptySentence()) != null) {
					String[] words = sequence.getWords();
					String[] stems = new String[words.length];
					
					String line = "";
					for (String word : words) line = line+" "+word;
					
					InstanceList instances = new InstanceList(this.pipes);
					instances.addThruPipe(new Instance(line.trim(), "X", "doc1", null));
					FeatureSequence tokens = (FeatureSequence) instances.get(0).getData();
					
					for (int i=0; i<words.length; i++) {
						stems[i] = (String) tokens.getObjectAtPosition(i);
					}
					
					TokenSequence newTokenSequence1 = new TokenSequence();
					TokenSequence newTokenSequence2 = new TokenSequence();
					
					for (int i=0; i<words.length; i++) {
						Token token = new Token();
						token.setWord(words[i]);
						token.setClass((words[i].startsWith(preffix1) ? mapping1 : mapping2).getType(), stems[i]);	
						(words[i].startsWith(preffix1) ? newTokenSequence1 : newTokenSequence2).addToken(token);
					}				
					
					lm1.processSentence(newTokenSequence1);
					lm2.processSentence(newTokenSequence2);
				}
				
				lm1.removeOutOfLimitNGrams(MIN_OCCURRENCE, true, false);
				lm1.computeProbabilities();
				lm1.getWordToClassMapping().smoothing();	
				
				lm2.removeOutOfLimitNGrams(MIN_OCCURRENCE, true, false);
				lm2.computeProbabilities();
				lm2.getWordToClassMapping().smoothing();	
				
				this.lowOccurredStems1 = 0;
				this.vocabulary1 = new HashSet<String>();
				for (String stem : this.mapping1.getClassVocabulary().getWords()) {		
					if (lm1.getClassNGram(new String[]{stem}) != null) {
						this.vocabulary1.add(stem);
					} else lowOccurredStems1++;
				}
				
				this.lowOccurredStems2 = 0;
				this.vocabulary2 = new HashSet<String>();
				for (String stem : this.mapping2.getClassVocabulary().getWords()) {		
					if (lm2.getClassNGram(new String[]{stem}) != null) {
						this.vocabulary2.add(stem);
					} else lowOccurredStems2++;
				}
			
			log.info("mapping finished...");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void createMatrix() {
		log.info("creating matrix of probabilities...");
		log.info("types="+lda.getAlphabet().size());
		this.topicWordProbabilities = new float[lda.getNumTopics()][lda.typeTopicCounts.length];
		
		double betaSum1 = 0d;
		double betaSum2 = 0d;
		int[] tokensPerTopics1 = new int[lda.getNumTopics()];
		int[] tokensPerTopics2 = new int[lda.getNumTopics()];
		
		for (int type = 0; type < lda.typeTopicCounts.length; type++) {
			String word = (String) lda.getAlphabet().lookupObject(type);
			
			if (word.startsWith(preffix1)) {
				betaSum1 += lda.beta;
			} else {
				betaSum2 += lda.beta;
			}
			
			int[] topicCounts = lda.typeTopicCounts[type];
			int index = 0;
			while (index < topicCounts.length && topicCounts[index] > 0) {
				int topic = topicCounts[index] & lda.topicMask;
				int count = topicCounts[index] >> lda.topicBits;
			
				if (word.startsWith(preffix1)) {
					tokensPerTopics1[topic] += count;
				} else {
					tokensPerTopics2[topic] += count;
				}
				index++;
			}
		}
		
		//System.out.println("betaSum1="+betaSum1);
		//System.out.println("betaSum2="+betaSum2);
		//System.out.println("tokensPerTopics1: "+Arrays.toString(tokensPerTopics1));
		//System.out.println("tokensPerTopics2: "+Arrays.toString(tokensPerTopics2));
		
		for (int topic=0; topic<lda.getNumTopics(); topic++) {
			for (int type = 0; type < lda.typeTopicCounts.length; type++) {
				String word = (String) lda.getAlphabet().lookupObject(type);
				if (word.startsWith(preffix1)) {
					topicWordProbabilities[topic][type] = (float) (lda.beta / ((float)tokensPerTopics1[topic] + betaSum1));
				} else {
					topicWordProbabilities[topic][type] = (float) (lda.beta / ((float)tokensPerTopics2[topic] + betaSum2));
				}
			}
		}
		
		for (int type = 0; type < lda.typeTopicCounts.length; type++) {
			String word = (String) lda.getAlphabet().lookupObject(type);
			int[] topicCounts = lda.typeTopicCounts[type];
			int index = 0;
			while (index < topicCounts.length && topicCounts[index] > 0) {
				int top = topicCounts[index] & lda.topicMask;
				int count = topicCounts[index] >> lda.topicBits;
				
				if (word.startsWith(preffix1)) {
					topicWordProbabilities[top][type] = (float) (((float)count + lda.beta) / ((float)tokensPerTopics1[top] + betaSum1));
				} else {
					topicWordProbabilities[top][type] = (float) (((float)count + lda.beta) / ((float)tokensPerTopics2[top] + betaSum2));
				}

				index++;
			}
		}
		
		log.info("creating matrix finished...");
		log.info("types="+lda.getAlphabet().size());
	}


	private void calculateOOVProbability() {
		this.lowOccurredStems1 = this.mapping1.getClassVocabulary().getSize() - vocabulary1.size();
		this.lowOccurredStems2 = this.mapping2.getClassVocabulary().getSize() - vocabulary2.size();
		
		log.info("Calculating OOV probability for model1: vocabulary min 5="+vocabulary1.size()+" rest="+lowOccurredStems1);
		log.info("Calculating OOV probability for model2: vocabulary min 5="+vocabulary2.size()+" rest="+lowOccurredStems2);
		topicWordProbabilitySums1 = new float[topicWordProbabilities.length];
		topicWordProbabilitySums2 = new float[topicWordProbabilities.length];	
		
		for (int topic=0; topic<lda.getNumTopics(); topic++) {
			for (String word : this.mapping1.getWordVocabulary().getWords()) {		
				InstanceList instances = new InstanceList(this.pipes);
				instances.addThruPipe(new Instance(word, "X", "doc1", null));
				FeatureSequence tokens = (FeatureSequence) instances.get(0).getData();
				String stem = (String) tokens.getObjectAtPosition(0);
				int type = lda.getAlphabet().lookupIndex(stem);
				
				if (vocabulary1.contains(stem)) {
					topicWordProbabilitySums1[topic] += topicWordProbabilities[topic][type] * this.mapping1.getWordProbCondByClass(word, stem);
				}
			}
			
			for (String word : this.mapping2.getWordVocabulary().getWords()) {		
				InstanceList instances = new InstanceList(this.pipes);
				instances.addThruPipe(new Instance(word, "X", "doc1", null));
				FeatureSequence tokens = (FeatureSequence) instances.get(0).getData();
				String stem = (String) tokens.getObjectAtPosition(0);
				int type = lda.getAlphabet().lookupIndex(stem);
				
				if (vocabulary2.contains(stem)) {
					topicWordProbabilitySums2[topic] += topicWordProbabilities[topic][type] * this.mapping2.getWordProbCondByClass(word, stem);
				}
			}
		}
	}
	
	
	public void setActualDocument(String[] words) {
		String line = "";
		for (String word : words) line = line+" "+word;
		
		InstanceList instances = new InstanceList(this.pipes);
		instances.addThruPipe(new Instance(line.trim(), "X", "doc1", null));
		
		LDAInferencer inferencer = new LDAInferencer(lda.typeTopicCounts, lda.tokensPerTopic,
				   lda.getData().get(0).instance.getDataAlphabet(),
				   lda.alpha, lda.beta, lda.betaSum);
		
		inferencer.sample(instances.get(0), 100, 10, 10);
		this.actualTopics = inferencer.getTopics();
		
		FeatureSequence tokens = (FeatureSequence) instances.get(0).getData();
		this.actualWords = new String[words.length];
		for (int i=0; i<actualWords.length; i++) {
			this.actualWords[i] = (String) tokens.getObjectAtPosition(i);
		}		
		
		actualTopicsHistogram = new int[lda.getNumTopics()];
		for (int topic : actualTopics) if (topic!=-1) actualTopicsHistogram[topic]++;
		
		/*
		wordToTopic = new HashMap<String, Integer>();
		for (int i=0; i<actualTopics.length; i++) {
			wordToTopic.put(this.actualWords[i], this.actualTopics[i]);
		}*/
		
		this.topicDenominator = 0;
		for (int topic=0; topic<lda.getNumTopics(); topic++) {
			this.topicDenominator += this.actualTopicsHistogram[topic] + lda.alpha[topic];
		}	
		
		float sum1 = 0;
		float sum2 = 0;
		for (int topic = 0; topic<lda.numTopics; topic++) {
			sum1 += getTopicProbability(topic) * this.topicWordProbabilitySums1[topic];
			sum2 += getTopicProbability(topic) * this.topicWordProbabilitySums2[topic];
		}
		
		this.probabilityOfOOV1 = (1.0f - sum1) / (float) lowOccurredStems1;
		this.probabilityOfOOV2 = (1.0f - sum2) / (float) lowOccurredStems2;
	}
	
	@Override
	public String getTitle() {
		return "parallel LDA ("+this.lda.numTopics+" topics)";
	}

	@Override
	public float getWordCondProb(TokenSequence sequence) {
		String words[] = sequence.getWords();
		String origWord = words[words.length - 1];
		
		InstanceList instances = new InstanceList(this.pipes);
		instances.addThruPipe(new Instance(origWord, "X", "doc1", null));
		FeatureSequence tokens = (FeatureSequence) instances.get(0).getData();
		
		String word = (String) tokens.getObjectAtPosition(0);
		int type = tokens.getIndexAtPosition(0);
		
		if (word.startsWith(preffix1)) {
			if (type == -1 || type >= lda.numTypes || !this.vocabulary1.contains(word)) {
				return probabilityOfOOV1;
			}
		} else {
			if (type == -1 || type >= lda.numTypes || !this.vocabulary2.contains(word)) {
				return probabilityOfOOV2;
			}
		}

		double sum = 0;
		for (int topic = 0; topic<lda.numTopics; topic++) {
			double wordTopicProb = topicWordProbabilities[topic][type];
			double topicProb = (double)(this.actualTopicsHistogram[topic] + lda.alpha[topic]) / this.topicDenominator;
			sum += topicProb * wordTopicProb;
		}
		
		sum *= (word.startsWith(preffix1) ? mapping1 : mapping2).getWordProbCondByClass(origWord, word);		
		return (float) sum;
	}

	public float getTopicProbability(int topic) {
		return (float) (this.actualTopicsHistogram[topic] + lda.alpha[topic]) / (float)this.topicDenominator;
	}
	
	public void saveForMila(String dir) throws IOException {
		//-----------write lda vocabulary 1--------------
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dir+"/lda-vocabulary1.txt"), "UTF-8"));
		for (String word : this.vocabulary1) {
			if (word.startsWith(this.preffix1)) {
				word = word.substring(preffix1.length(), word.length());
			}
			bw.write(word+"\n");
		}
		bw.flush();
		bw.close();
		
		//----------write lda vocabulary 2-----------------
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dir+"/lda-vocabulary2.txt"), "UTF-8"));
		for (String word : this.vocabulary2) {
			if (word.startsWith(this.preffix2)) {
				word = word.substring(preffix2.length(), word.length());
			}
			bw.write(word+"\n");
		}
		bw.flush();
		bw.close();
		
		//-----------write full vocabulary 1--------------
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dir+"/full-vocabulary1.txt"), "UTF-8"));
		for (int type = 0; type < lda.typeTopicCounts.length; type++) {
			String word = (String) lda.getAlphabet().lookupObject(type);
			if (word.startsWith(preffix1)) {
				word = word.substring(preffix1.length(), word.length());
				bw.write(word+"\n");
			}
		}
		bw.flush();
		bw.close();
		
		//-----------write full vocabulary 1--------------
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dir+"/full-vocabulary2.txt"), "UTF-8"));
		for (int type = 0; type < lda.typeTopicCounts.length; type++) {
			String word = (String) lda.getAlphabet().lookupObject(type);
			if (word.startsWith(preffix2)) {
				word = word.substring(preffix2.length(), word.length());
				bw.write(word+"\n");
			}
		}
		bw.flush();
		bw.close();
		
		//-----------save alphas----------------
		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dir+"/alphas.txt"), "UTF-8"));
		for (int topic = 0; topic<lda.numTopics; topic++) {
			bw.write(topic+"\t"+lda.alpha[topic]+"\n");
		}
		bw.flush();
		bw.close();
		
		DataOutputStream dos = new DataOutputStream(new FileOutputStream(dir+"/alphas.bin"));
		for (int topic = 0; topic<lda.numTopics; topic++) {
			dos.writeFloat((float)lda.alpha[topic]);
		}
		
		//-----------save matrices----------------
		saveMatrix(vocabulary1, dir+"/matrix-word-topics1.bin");
		saveMatrix(vocabulary2, dir+"/matrix-word-topics2.bin");
		
	}
	
	public void inferTopicsForData(String inputFileName, String prefix, String outputFileName) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFileName), "UTF-8"));
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName), "UTF-8"));
		
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] words = line.split("[ \\t\\s\\n]+");
			for (int i=0; i<words.length; i++) {
				words[i] = prefix+words[i];
			}
		
			setActualDocument(words);
			for (int topic : actualTopics) {
				bw.write(topic+"\t");
			}
			bw.write("\n");
			bw.flush();
		}
		
		bw.close();
	}
	
	//topic, words
	private void saveMatrix(Set<String> vocabulary, String fileName) throws IOException {
		DataOutputStream dos = new DataOutputStream(new FileOutputStream(fileName));
		dos.writeInt(lda.numTopics);
		dos.writeInt(vocabulary.size());
		
		for (int topic=0; topic<lda.numTopics; topic++) {
			int index = 0;
			for (String word : vocabulary) {
				int type = lda.getAlphabet().lookupIndex(word);
				float prob = topicWordProbabilities[topic][type];
				//dos.writeFloat(Float.floatToRawIntBits(prob));
				dos.writeFloat(prob);
				if (topic == 0 && index < 100) System.out.print(prob+", ");
					
				index++;
			}
		}
		System.out.println();
		
		dos.flush();
		dos.close();
	}
	
	
	public void test(String[] words) throws FileNotFoundException {
		setActualDocument(words);
		/*
		System.out.println("model1 oov="+probabilityOfOOV1+" model2 oov="+probabilityOfOOV2);
		System.out.println("denominator="+topicDenominator);
		System.out.println("actual topics: "+Arrays.toString(this.actualTopics));
		System.out.println("topicWordProbabilitySums1: "+Arrays.toString(this.topicWordProbabilitySums1));
		System.out.println("topicWordProbabilitySums2: "+Arrays.toString(this.topicWordProbabilitySums2));
		*/
		
		//FileOutputStream fos = new FileOutputStream(new File("out.txt"));
		//PrintStream ps = new PrintStream(fos);
		//System.setOut(ps);
		
		
		float sum = 0;
		for (String word : this.mapping1.getWordVocabulary().getWords()) {
			sum += getWordCondProb(new TokenSequence(new String[]{word}));
			//System.out.println(word+" "+sum);
		}
		System.out.println("1model: suma pres slova "+sum);
		
		sum = 0;
		for (String word : this.mapping2.getWordVocabulary().getWords()) {
			sum += getWordCondProb(new TokenSequence(new String[]{word}));
			//System.out.println(word+" "+sum);
		}
		System.out.println("2model: suma pres slova "+sum);
	}
	
	public String toString() {
		String s = getTitle()+"1model: words="+this.mapping1.getWordVocabulary().getSize()+" stems="+this.mapping1.getClassVocabulary().getSize()+" stem min 5="+this.vocabulary1.size()+"\n";
		s += getTitle()+"2model: words="+this.mapping2.getWordVocabulary().getSize()+" stems="+this.mapping2.getClassVocabulary().getSize()+" stem min 5="+this.vocabulary2.size();
		return s;
	}
	
	@Override
	public float getWordNGramCount(TokenSequence arg0) {return 0;}

	@Override
	public float getWordUncondProb(TokenSequence arg0) {return 0;}

	public Stemmer getStemmer() {
		return stemmer;
	}

	public void setStemmer(Stemmer stemmer) {
		this.stemmer = stemmer;
	}


	public SerialPipes getPipes() {
		return pipes;
	}

	public void setPipes(SerialPipes pipes) {
		this.pipes = pipes;
	}

	public String[] getActualWords() {
		return actualWords;
	}

	public int[] getActualTopics() {
		return actualTopics;
	}
	
	public int getNumberOfTopics() {
		return lda.getNumTopics();
	}

	public ParallelTopicModel getMalletLDAModel() {
		return lda;
	}

	public float[][] getTopicWordProbabilities() {
		return topicWordProbabilities;
	}

	public Set<String> getVocabulary1() {
		return vocabulary1;
	}

	public Set<String> getVocabulary2() {
		return vocabulary2;
	}
}
