package routing.community;

import core.DTNHost;

import java.util.Comparator;

/**
 * Created by esquire on 4/6/2016.
 */
public class CentralityHost implements Comparator<CentralityHost> {
    private DTNHost host;
    private double centrality;

    public CentralityHost(DTNHost host, double centrality) {
        this.host = host;
        this.centrality = centrality;
    }

    public DTNHost getHost() {
        return host;
    }

    public void setHost(DTNHost host) {
        this.host = host;
    }

    public double getCentrality() {
        return centrality;
    }

    public void setCentrality(double centrality) {
        this.centrality = centrality;
    }

    @Override
    public int compare(CentralityHost o1, CentralityHost o2) {
        if(o1.getCentrality() < o2.getCentrality()) {
            return 1; // This is backwards to allow for a decreasing sort
        } else if(o1.getCentrality() == o2.getCentrality()) {
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CentralityHost that = (CentralityHost) o;

        return host != null ? host.equals(that.host) : that.host == null;

    }

    @Override
    public int hashCode() {
        return host != null ? host.hashCode() : 0;
    }
}
