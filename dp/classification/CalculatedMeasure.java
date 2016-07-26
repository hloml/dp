package dp.classification;

/**
 * @author Ladislav Hlom
 * Třída pro udržení klasifikačních výsledků
 *
 */
public class CalculatedMeasure {

	public double precision;
	public double recall;
	public double fMeasure;
	
	
	public CalculatedMeasure(double precision, double recall, double fMeasure) {
		this.precision = precision;
		this.recall = recall;
		this.fMeasure = fMeasure;
	}
	
	
	public double getPrecision() {
		return precision;
	}
	public void setPrecision(double precision) {
		this.precision = precision;
	}
	public double getRecall() {
		return recall;
	}
	public void setRecall(double recall) {
		this.recall = recall;
	}
	public double getfMeasure() {
		return fMeasure;
	}
	public void setfMeasure(double fMeasure) {
		this.fMeasure = fMeasure;
	}
	
}
