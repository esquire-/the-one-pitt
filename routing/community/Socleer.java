/*
 * @(#)Socleer.java
 *
 * Modified from DistributedBubbleRap.java: Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

import java.util.*;

public class Socleer
				implements RoutingDecisionEngine, routing.community.CommunityDetectionEngine
{
	/** Community Detection Algorithm to employ -setting id {@value} */
	public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";
	/** Centrality Computation Algorithm to employ -setting id {@value} */
	public static final String CENTRALITY_ALG_SETTING = "centralityAlg";

	protected Map<DTNHost, Double> startTimestamps;
	protected Map<DTNHost, List<routing.community.Duration>> connHistory;

	protected routing.community.CommunityDetection community;
	protected routing.community.Centrality centrality;

	/**
	 * Constructs a DistributedBubbleRap Decision Engine based upon the settings
	 * defined in the Settings object parameter. The class looks for the class
	 * names of the community detection and centrality algorithms that should be
	 * employed used to perform the routing.
	 *
	 * @param s Settings to configure the object
	 */
	public Socleer(Settings s)
	{
		if(s.contains(COMMUNITY_ALG_SETTING))
			this.community = (CommunityDetection)
				s.createIntializedObject(s.getSetting(COMMUNITY_ALG_SETTING));
		else
			this.community = new SimpleCommunityDetection(s);

		if(s.contains(CENTRALITY_ALG_SETTING))
			this.centrality = (Centrality)
				s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
		else
			this.centrality = new SWindowCentrality(s);
	}

	/**
	 * Constructs a DistributedBubbleRap Decision Engine from the argument
	 * prototype.
	 *
	 * @param proto Prototype DistributedBubbleRap upon which to base this object
	 */
	public Socleer(Socleer proto)
	{
		this.community = proto.community.replicate();
		this.centrality = proto.centrality.replicate();
		startTimestamps = new HashMap<DTNHost, Double>();
		connHistory = new HashMap<DTNHost, List<Duration>>();
	}

	public void connectionUp(DTNHost thisHost, DTNHost peer){
        double localCent = getOtherDecisionEngine(peer).getLocalCentrality();
        double globalCent = getOtherDecisionEngine(peer).getGlobalCentrality();
        socleerHosts.add(new CentralityHost(peer, localCent));
        socleerHosts.add(new CentralityHost(peer, globalCent));
    }

	/**
	 * Starts timing the duration of this new connection and informs the community
	 * detection object that a new connection was formed.
	 *
	 * @see RoutingDecisionEngine#doExchangeForNewConnection(Connection, DTNHost)
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		Socleer de = this.getOtherDecisionEngine(peer);
		
		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
		
		this.community.newConnection(myHost, peer, de.community);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		// add this connection to the list
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community;
		
		// inform the community detection object that a connection was lost.
		// The object might need the whole connection history at this point.
		community.connectionLost(thisHost, peer, peerCD, history);
		
		startTimestamps.remove(peer);
	}

	public boolean newMessage(Message m)
	{
		return true; // Always keep and attempt to forward a created message
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		return m.getTo() == aHost; // Unicast Routing
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		return m.getTo() != thisHost;
	}

    public static final int SOCLEER_SIZE = -1; // n, set to -1 for infinite
    public static final int SOCLEER_DRAW = 5; // 1-10

    private int min(int a, int b) {
        if(a > b) {
            return b;
        } else {
            return a;
        }
    }

    ArrayList<CentralityHost> socleerHosts = new ArrayList<>();

	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
		if(m.getTo() == otherHost) return true; // trivial to deliver to final dest
		int size = SOCLEER_SIZE < 0 ? socleerHosts.size() : SOCLEER_SIZE;

        // Socleer (?)
        socleerHosts.sort(new CentralityHost(null, 0d));
		List<CentralityHost> tmpList = new ArrayList<>(socleerHosts); // Shadow copy
		tmpList = tmpList.subList(0, min(tmpList.size(), size)); // Create list of grabable elements

        List<DTNHost> holdList = new ArrayList<>();
        Random r = new Random();
        for(int i = 0; i < SOCLEER_DRAW; i++) { // For each host we're drawing
            int select = r.nextInt(tmpList.size());
            holdList.add(tmpList.get(select).getHost());
			tmpList.remove(select);
        }

        if(holdList.contains(otherHost)) {
            return true;
        } else {
            return false;
        }
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		// DiBuBB allows a node to remove a message once it's forwarded it into the
		// local community of the destination
		Socleer de = this.getOtherDecisionEngine(otherHost);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		Socleer de = this.getOtherDecisionEngine(hostReportingOld);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public RoutingDecisionEngine replicate()
	{
		return new Socleer(this);
	}
	
	protected boolean commumesWithHost(DTNHost h)
	{
		return community.isHostInCommunity(h);
	}
	
	protected double getLocalCentrality()
	{
		return this.centrality.getLocalCentrality(connHistory, community);
	}
	
	protected double getGlobalCentrality()
	{
		return this.centrality.getGlobalCentrality(connHistory);
	}

	private Socleer getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (Socleer) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
	}

	public Set<DTNHost> getLocalCommunity() {return this.community.getLocalCommunity();}
}
