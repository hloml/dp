


import java.io.Serializable;

import ws.stemmer.Stemmer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

public class ParallelStemmerPipe extends Pipe implements Serializable {

	private static final long serialVersionUID = -5954469241795647737L;
	private transient Stemmer stemmer1;
	private transient Stemmer stemmer2;
	private String prefix1;
	private String prefix2;
	
	public ParallelStemmerPipe(String prefix1, String prefix2, Stemmer stemmer1, Stemmer stemmer2) {
		this.prefix1 = prefix1;
		this.prefix2 = prefix2;
		this.stemmer1 = stemmer1;
		this.stemmer2 = stemmer2;
	}
	
	@Override
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		
		for (int i = 0; i < ts.size(); i++) {
			String word = ts.get(i).getText();
			String stem;
			if (word.startsWith(prefix1)) {
				String wordWithoutPrefix = word.substring(prefix1.length(), word.length());
				stem = prefix1+stemmer1.getClass(wordWithoutPrefix);
				
			} else if (word.startsWith(prefix2)) {
				String wordWithoutPrefix = word.substring(prefix2.length(), word.length());
				stem = prefix2+stemmer2.getClass(wordWithoutPrefix);
			} else {
				stem = word;
				//System.out.println("divnej prefix pro slovo: "+word);
			}

			ts.set(i, new Token(stem));
		}

		return carrier;
	}

	public Stemmer getStemmer1() {
		return stemmer1;
	}

	public void setStemmer1(Stemmer stemmer) {
		this.stemmer1 = stemmer;
	}
	
	public Stemmer getStemmer2() {
		return stemmer2;
	}

	public void setStemmer2(Stemmer stemmer) {
		this.stemmer2 = stemmer;
	}
	
}
