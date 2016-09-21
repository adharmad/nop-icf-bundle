package nopbundle;

import org.identityconnectors.framework.spi.AbstractConfiguration;

public class NopBundleConfiguration extends AbstractConfiguration {

    private String strConfigParam;
    private boolean strict = false;

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }
    
    public void validate() {
    }

	public String getStrConfigParam() {
		return strConfigParam;
	}

	public void setStrConfigParam(String strConfigParam) {
		this.strConfigParam = strConfigParam;
	}
    
    

}
