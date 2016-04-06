/*
 * @(#)DistributedBubbleRap.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

import java.util.*;

/**
 * <p>Implements the Distributed BubbleRap Routing Algorithm from Hui et al. 
 * 2008 (Bibtex record included for convenience). The paper is a bit fuzzy on 
 * thevactual implementation details. Choices exist for methods of community
 * detection (SIMPLE, K-CLIQUE, MODULARITY) and local centrality approximation
 * (DEGREE, S-WINDOW, C-WINDOW).</p> 
 * 
 * <p>In general, each node maintains an idea of it's local community, a group 
 * of nodes it meets with frequently. It also approximates its centrality within
 * the social network defined by this local community and within the global
 * social network defined by all nodes.</p>
 * 
 * <p>When a node has a message for a destination, D, and D is not part of its 
 * local community, it forwards the message to "more globally central" nodes,
 * those that estimate a higher global centrality value. The intuition here is 
 * that nodes in the center of the social network are more likely to contact the
 * destination. In this fashion the message bubbles up social network to more
 * central nodes until a node is found that reports D in its local community.
 * At this point, the message is only routed with in the nodes of the local 
 * community and propagated towards more locally central nodes or the 
 * destination until delivered.<p>
 * 
 * <pre>
 * \@inproceedings{1374652,
 *	Address = {New York, NY, USA},
 *	Author = {Hui, Pan and Crowcroft, Jon and Yoneki, Eiko},
 *	Booktitle = {MobiHoc '08: Proceedings of the 9th ACM international symposium 
 *		on Mobile ad hoc networking and computing},
 *	Doi = {http://doi.acm.org/10.1145/1374618.1374652},
 *	Isbn = {978-1-60558-073-9},
 *	Location = {Hong Kong, Hong Kong, China},
 *	Pages = {241--250},
 *	Publisher = {ACM},
 *	Title = {BUBBLE Rap: Social-based Forwarding in Delay Tolerant Networks},
 *	Url = {http://portal.acm.org/ft_gateway.cfm?id=1374652&type=pdf&coll=GUIDE&dl=GUIDE&CFID=55195392&CFTOKEN=93998863},
 *	Year = {2008}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class Socleer
				implements RoutingDecisionEngine, CommunityDetectionEngine
{
	/** Community Detection Algorithm to employ -setting id {@value} */
	public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";
	/** Centrality Computation Algorithm to employ -setting id {@value} */
	public static final String CENTRALITY_ALG_SETTING = "centralityAlg";

	protected Map<DTNHost, Double> startTimestamps;
	protected Map<DTNHost, List<Duration>> connHistory;

	protected CommunityDetection community;
	protected Centrality centrality;

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

    public static final int SOCLEER_SIZE = 10;

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

        // Socleer (?)
        socleerHosts.sort(new CentralityHost(null, 0d));

        List<CentralityHost> holdingList = socleerHosts.subList(0, min(socleerHosts.size(), SOCLEER_SIZE));
        List<DTNHost> hostList = new ArrayList<>();
        for(CentralityHost ch : holdingList) {
            hostList.add(ch.getHost());
        }
        if(hostList.contains(otherHost)) {
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
