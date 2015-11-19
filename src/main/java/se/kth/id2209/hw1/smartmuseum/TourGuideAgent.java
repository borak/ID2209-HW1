package se.kth.id2209.hw1.smartmuseum;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.proto.states.MsgReceiver;
import se.kth.id2209.hw1.exhibition.Artifact;
import se.kth.id2209.hw1.profiler.UserProfile;
import se.kth.id2209.hw1.util.DFUtilities;
import se.kth.id2209.hw1.util.Ontologies;

/**
 * Tour Guide Agent retrieves the information about artifacts in the
 * gallery/museum and builds a virtual tour (upon the request) for profiler
 * agent.
 *
 * –The virtual tour contains list of related items (based on user’s interest,
 * age, etc..)
 *
 *  Tour Guide agent interacts with Curator Agent in order to build the virtual
 * tour.
 *
 * TODO: Implement behaviours. Behaviors should correspond to each category
 * below:  Simple Behavior (at least 5 different behaviors): – CyclicBehaviour,
 * MsgReceiver, OneShotBehaviour, SimpleAchieveREInitiator,
 * SimpleAchieveREResponder, TickerBehaviour, WakerBehaviour  Composite
 * Behaviors (at least 2 different behaviors): – ParallelBehaviour,
 * FSMBehaviour, SequentialBehaviour
 *
 * @author Kim
 */
public class TourGuideAgent extends Agent {

    private static final long serialVersionUID = 5883088851872677769L;
    private HashMap<AID, UserProfile> users = new HashMap<>();
    private TGAMsgReceiverBehaviour msgReceiver;
    private Map<String, AID> requests = new HashMap<String, AID>();
    private Map<AID, List<ACLMessage>> responses = new HashMap<AID, List<ACLMessage>>();
    static Lock usersLock = new ReentrantLock();

    @Override
    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("Tour-Guide-agent");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        ParallelBehaviour par = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
        msgReceiver = new TGAMsgReceiverBehaviour(this,
                null, MsgReceiver.INFINITE, new DataStore(), null);
        par.addSubBehaviour(msgReceiver);
        par.addSubBehaviour(new PresentingRecommendationsBehaviour(this, 10000));
    }

    // TODO: se till att denna kallas då en profiler vill starta en tour
    // för att requesta artifact baserat på intresse
    @SuppressWarnings("serial")
    void startTour() {

        SequentialBehaviour seq = new SequentialBehaviour();
        seq.addSubBehaviour(new OneShotBehaviour() {

            @Override
            public void action() {
                usersLock.lock();
                try {
                    Iterator<Entry<AID, UserProfile>> it = getUsers().entrySet().iterator();
                    ACLMessage msg = null;
                    
                    while (it.hasNext()) {
                        Entry<AID, UserProfile> entry = it.next();
                        AID aid = entry.getKey();
                        UserProfile profile = entry.getValue();
                        List<String> interests = profile.getInterests();
                        ArrayList<Artifact.GENRE> genres = new ArrayList<>();
                        for (String s : interests) {
                            genres.add(Artifact.GENRE.valueOf(s));
                        }                        
                        AID caid = DFUtilities.searchDF(TourGuideAgent.this, "Curator-agent");
                        if (caid == null) {
                            block();
                        }                        
                        try {
                            msg.addReceiver(caid);
                            msg.setOntology(Ontologies.QUERY_ARTIFACTS);
                            msg.setContentObject((Serializable) genres);
                            
                            requests.put(msg.getConversationId(), aid);
                            send(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    usersLock.unlock();
                }
            }
        });
        addBehaviour(seq);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Agent " + getAID().getName() + " is terminating.");
    }

	public Map<String, AID> getRequests() {
		return requests;
	}

	public void setRequests(Map<String, AID> requests) {
		this.requests = requests;
	}

	public Map<AID, List<ACLMessage>> getResponses() {
		return responses;
	}

	public void setResponses(Map<AID, List<ACLMessage>> responses) {
		this.responses = responses;
	}

	public HashMap<AID, UserProfile> getUsers() {
		return users;
	}

	public void setUsers(HashMap<AID, UserProfile> users) {
		this.users = users;
	}
}
