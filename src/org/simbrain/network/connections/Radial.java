/*
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.network.connections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.simbrain.network.core.Neuron;
import org.simbrain.network.core.Synapse;
import org.simbrain.network.groups.SynapseGroup;
import org.simbrain.util.SimbrainConstants.Polarity;
import org.simbrain.util.math.ProbDistribution;

/**
 *
 * This connection type makes four types of distance-based connection
 * probabilistically.
 * <ol>
 * <li>Excitatory to Excitatory (EE)</li>
 * <li>Excitatory to Inhibitory (EI)</li>
 * <li>Inhibitory to Inhibitory (II)</li>
 * <li>Inhibitory to Excitatory (IE)</li>
 * </ol>
 *
 * In each case, the probability of a connection is more likely the closer two
 * neurons of the relevant type are to each other. Probabilities of connection
 * are given by an exponential probability distribution e^-( D(a, b) / (λ^2) )
 * where D is distance in pixels.
 *
 * Lambda controls the number of pixels over which to expect connections to be
 * made.
 *
 * Any of the 4 constants for the 4 cases can be set to a value between 0 and 1.
 * Set to 0, if you want no connections of that type to be made. Set to 1 to
 * have it make the most connections possible given the exponential
 * distribution.
 *
 * @author Zoë Tosi
 *
 * TODO: Add a sparsity constraint, such that connections are still chosen stochastically
 * based on distance, but a specific number of connections are guaranteed to be made.
 *
 */
public class Radial extends Sparse {

    /** For neurons with no polarity. */
    public static final double DEFAULT_DIST_CONST = 0.25;

    public static final double DEFAULT_EE_CONST = 0.3;

    public static final double DEFAULT_EI_CONST = 0.2;

    public static final double DEFAULT_IE_CONST = 0.4;

    public static final double DEFAULT_II_CONST = 0.1;

    public static final double DEFAULT_LAMBDA = 2.5;

    /** The connection constant for connections between 2 excitatory neurons. */
    private double eeDistConst = DEFAULT_EE_CONST;

    /**
     * The connection constant for connection from an excitatory to an
     * inhibitory neuron.
     */
    private double eiDistConst = DEFAULT_EI_CONST;

    /**
     * The connection constant for connection from an inhibitory to an
     * excitatory neuron.
     */
    private double ieDistConst = DEFAULT_IE_CONST;

    /** The connection constant for connections between 2 inhibitory neurons. */
    private double iiDistConst = DEFAULT_II_CONST;

    /**
     * The connection constant for general connections. Used in cases where
     * neurons have no explicit polarity.
     */
    private double distConst = DEFAULT_DIST_CONST;

    /**
     * A regulating constant governing overall connection density. Higher values
     * create denser connections. Lambda can be thought of as the average 
     * connection distance.
     */
    private double lambda = DEFAULT_LAMBDA;
    
    private SynapseGroup synapseGroup;

    /**
     *
     * @param source the source neurons.
     * @param target the target neurons.
     * @param eeDistConst the connection constant for connections between 2 excitatory neurons.
     * @param eiDistConst the connection constant for connection from an excitatory to an inhibitory neuron.
     * @param ieDistConst the connection constant for connection from an inhibitory to an excitatory neuron.
     * @param iiDistConst the connection constant for connections between 2 inhibitory neurons. 
     * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit polarity.
     * @param lambda average connection distance.
     * @param loose 
     * @return synapses
     */
    public static List<Synapse> connectRadialPolarized(
        final List<Neuron> source, final List<Neuron> target,
        double eeDistConst, double eiDistConst, double ieDistConst,
        double iiDistConst, double distConst, double lambda, boolean loose) {
        // Pre-allocating assuming that if one is using this as a connector
        // then they are probably not going to have greater than 25%
        // connectivity
        List<Synapse> synapses = new ArrayList<Synapse>(source.size()
            * target.size() / 4);
        for (Neuron src : source) {
            for (Neuron tar : target) {
                double randVal = Math.random();
                double probability;
                if (src.getPolarity() == Polarity.EXCITATORY) {
                    if (tar.getPolarity() == Polarity.EXCITATORY) {
                        probability = calcConnectProb(src, tar, eeDistConst,
                            lambda);
                    } else if (tar.getPolarity() == Polarity.INHIBITORY) {
                        probability = calcConnectProb(src, tar, eiDistConst,
                            lambda);
                    } else {
                        probability = calcConnectProb(src, tar, distConst,
                            lambda);
                    }
                } else if (src.getPolarity() == Polarity.INHIBITORY) {
                    if (tar.getPolarity() == Polarity.EXCITATORY) {
                        probability = calcConnectProb(src, tar, ieDistConst,
                            lambda);
                    } else if (tar.getPolarity() == Polarity.INHIBITORY) {
                        probability = calcConnectProb(src, tar, iiDistConst,
                            lambda);
                    } else {
                        probability = calcConnectProb(src, tar, distConst,
                            lambda);
                    }
                } else {
                    probability = calcConnectProb(src, tar, distConst,
                        lambda);
                }
                if (randVal < probability) {
                    Synapse s = new Synapse(src, tar);
                    synapses.add(s);
                    if (loose) {
                        src.getNetwork().addSynapse(s);
                    }
                }
            }
        }
        return synapses;
    }

    /**
     *
     * @param source the source neurons
     * @param target the target neurons
     * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit polarity.
     * @param lambda average connection distance.
     * @param loose 
     * @return array of synapses
     */
    public static List<Synapse> connectRadialNoPolarity(
        final List<Neuron> source, final List<Neuron> target, double distConst,
        double lambda, boolean loose) {
        // Pre-allocating assuming that if one is using this as a connector
        // then they are probably not going to have greater than 25%
        // connectivity
        List<Synapse> synapses = new ArrayList<Synapse>(source.size()
            * target.size() / 4);
        for (Neuron src : source) {
            for (Neuron tar : target) {
                double randVal = Math.random();
                double probability = calcConnectProb(src, tar, distConst,
                    lambda);
                if (randVal < probability) {
                    Synapse s = new Synapse(src, tar);
                    synapses.add(s);
                    if (loose) {
                        src.getNetwork().addSynapse(s);
                    }
                }
            }
        }
        return synapses;
    }

    /**
     * Default constructor
     */
    public Radial() {
    	this.setPermitDensityEditing(false);
    }

    /**
     * @param lambda average connection distance.
     */
    public Radial(double lambda) {
        this();
        this.lambda = lambda;
    }

    /**
     * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit polarity.
     * @param lambda average connection distance.
     */
    public Radial(double distConst, double lambda) {
    	this();
        this.distConst = distConst;
        this.lambda = lambda;
    }

    /**
     *
     * @param eeDistConst the connection constant for connections between 2 excitatoy neurons
     * @param eiDistConst the connection constant for connection from an excitatory to an inhibitory neuron.
     * @param ieDistConst the connectino constant for connection from an inhibitory to an excitatory neuron.
     * @param iiDistConst the conneciton constant for connections between 2 inhibitory neurons.
     * @param lambda average connection distance.
     */
    public Radial(double eeDistConst, double eiDistConst, double ieDistConst,
        double iiDistConst, double lambda) {
    	this();
        this.eeDistConst = eeDistConst;
        this.eiDistConst = eiDistConst;
        this.ieDistConst = ieDistConst;
        this.iiDistConst = iiDistConst;
        this.lambda = lambda;
    }

    /**
     * {@inheritDoc} Specifically: Connects neurons based on a probability
     * function related to their distance from one another, which exponentially
     * decays with distance.
     */
    @Override
    public void connectNeurons(SynapseGroup synGroup) {
        this.synapseGroup = synGroup;
        List<Neuron> source = synGroup.getSourceNeurons();
        List<Neuron> target = synGroup.getTargetNeurons();
        List<Synapse> synapses;
        if (source.size() < 500) {
        	synapses = connectRadialPolarized(source, target,
        			eeDistConst, eiDistConst, ieDistConst, iiDistConst,
        			distConst, lambda, false);
            for (Synapse s : synapses) {
                synGroup.addNewSynapse(s);
            }
        } else {
        	List<Callable<Collection<Synapse>>> workers =
        			new ArrayList<Callable<Collection<Synapse>>>();
        	int threads = Runtime.getRuntime().availableProcessors();
        	int idealShare = (int) Math.floor(source.size() / threads);
        	int remaining = source.size();
        	Iterator<Neuron> srcIter = source.iterator();
        	List<Neuron> srcChunk;
        	double runningPercentEx = 0;
        	for (int i = 0; i < threads; i++) {
        		srcChunk = new ArrayList<Neuron>((int) Math.ceil((idealShare
        				* 2) / 0.75));
        		int share;
        		if (remaining < idealShare * 2) {
        			share = remaining;
        		} else {
        			share = idealShare;
        		}
        		int j = 0;
        		while (j < share) {
        			Neuron n = srcIter.next();
        			srcChunk.add(n);
        			if (n.isPolarized()) {
        				if (Polarity.EXCITATORY == n.getPolarity()) {
        					runningPercentEx++;
        				}
        			}
        			j++;
        		}
        		remaining -= j;
        		workers.add(new ConnectorService(srcChunk, target, false));
        	}
        	runningPercentEx /= source.size();
        	synGroup.setExcitatoryRatio(runningPercentEx);
        	ExecutorService ex = Executors.newFixedThreadPool(threads);
        	List<Future<Collection<Synapse>>> generatedSyns;
        	try {
				generatedSyns = ex.invokeAll(workers);
				ex.shutdown();
				ex.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
        	int numSyns = 0;
        	for (Future<Collection<Synapse>> future : generatedSyns) {
        		try {
					numSyns += future.get().size();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
        	}
        	synGroup.preAllocateSynapses(numSyns);
        	for (Future<Collection<Synapse>> future : generatedSyns) {
        		try {
					for(Synapse s : future.get()) {
						synGroup.addNewSynapse(s);
					}
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
        	}
        }

        if (synGroup.isRecurrent()) {
            connectionDensity = (double) synGroup.size() /
                (synGroup.getSourceNeuronGroup().size()
                * (synGroup.getSourceNeuronGroup().size() - 1));
        } else {
            connectionDensity = (double) synGroup.size() /
                (synGroup.getSourceNeuronGroup().size()
                * synGroup.getTargetNeuronGroup().size());
        }
        source = null;
        target = null;
        synapses = null;
        Runtime.getRuntime().gc();
    }

    /**
     *
     * @param src the source neuron.
     * @param tar the target neuron.
     * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit polarity.
     * @param lambda average connection distance.
     * @return
     */
    private static double calcConnectProb(Neuron src, Neuron tar,
        double distConst, double lambda) {
        double dist = -getRawDist(src, tar);
        double exp = Math.exp(dist / (lambda * lambda));
        if (exp == 1.0) { // Same location == same neuron: cheapest way to
            // prevent self connections
            exp = 0.0;
        }
        return distConst * exp;
    }

    /**
     *
     * @param n1 neuron one
     * @param n2 neuron two
     * @return
     */
    private static double getRawDist(Neuron n1, Neuron n2) {
        double x2 = (n1.getX() - n2.getX());
        x2 *= x2;
        double y2 = (n1.getY() - n2.getY());
        y2 *= y2;
        double z2 = (n1.getZ() - n2.getZ());
        z2 *= z2;
        return x2 + y2 + z2;
    }

    @Override
    public String toString() {
        return "Radial";
    }

    public double getEeDistConst() {
        return eeDistConst;
    }

    public void setEeDistConst(double eeDistConst) {
        this.eeDistConst = eeDistConst;
    }

    public double getEiDistConst() {
        return eiDistConst;
    }

    public void setEiDistConst(double eiDistConst) {
        this.eiDistConst = eiDistConst;
    }

    public double getIeDistConst() {
        return ieDistConst;
    }

    public void setIeDistConst(double ieDistConst) {
        this.ieDistConst = ieDistConst;
    }

    public double getIiDistConst() {
        return iiDistConst;
    }

    public void setIiDistConst(double iiDistConst) {
        this.iiDistConst = iiDistConst;
    }

    public double getDistConst() {
        return distConst;
    }

    public void setDistConst(double distConst) {
        this.distConst = distConst;
    }

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    private class ConnectorService implements Callable<Collection<Synapse>> {
    	
    	private final Collection<Neuron> srcColl;
    	
    	private final Collection<Neuron> targColl;
    	
    	private final boolean loose;
    	
    	public ConnectorService(final Collection<Neuron> srcColl,
    			final Collection<Neuron> targColl, final boolean loose) {
    		this.srcColl = srcColl;
    		this.targColl = targColl;
    		this.loose = loose;
    	}

		@Override
		public Collection<Synapse> call() throws Exception {
			// Attempting to pre-allocate... assumes that connection density
			// will be less than #src * #tar * 0.2 or 20% connectivity
			List<Synapse> synapses = new ArrayList<Synapse>(
					(int) Math.ceil(srcColl.size() * targColl.size() * 0.2
							* 0.75));
			for (Neuron src : srcColl) {
	            for (Neuron tar : targColl) {
	                double randVal = ProbDistribution.UNIFORM.nextRand(0, 1);
	                double probability;
	                if (src.getPolarity() == Polarity.EXCITATORY) {
	                    if (tar.getPolarity() == Polarity.EXCITATORY) {
	                        probability = calcConnectProb(src, tar, eeDistConst,
	                            lambda);
	                    } else if (tar.getPolarity() == Polarity.INHIBITORY) {
	                        probability = calcConnectProb(src, tar, eiDistConst,
	                            lambda);
	                    } else {
	                        probability = calcConnectProb(src, tar, distConst,
	                            lambda);
	                    }
	                } else if (src.getPolarity() == Polarity.INHIBITORY) {
	                    if (tar.getPolarity() == Polarity.EXCITATORY) {
	                        probability = calcConnectProb(src, tar, ieDistConst,
	                            lambda);
	                    } else if (tar.getPolarity() == Polarity.INHIBITORY) {
	                        probability = calcConnectProb(src, tar, iiDistConst,
	                            lambda);
	                    } else {
	                        probability = calcConnectProb(src, tar, distConst,
	                            lambda);
	                    }
	                } else {
	                    probability = calcConnectProb(src, tar, distConst,
	                        lambda);
	                }
	                if (randVal < probability) {
	                    Synapse s = new Synapse(src, tar);
	                    synapses.add(s);
	                    if (loose) {
	                        src.getNetwork().addSynapse(s);
	                    }
	                }
	            }
	        }
	        return synapses;
		}
    	
    }
    
    public class DensityEstimator implements Runnable {

        private double estimateDensity;

        @Override
        public void run() {
            int count = 0;
            for (Neuron src : synapseGroup.getSourceNeurons()) {
                for (Neuron tar : synapseGroup.getTargetNeurons()) {
                    double randVal = Math.random();
                    double probability;
                    if (src.getPolarity() == Polarity.EXCITATORY) {
                        if (tar.getPolarity() == Polarity.EXCITATORY) {
                            probability =
                                calcConnectProb(src, tar, eeDistConst,
                                    lambda);
                        } else if (tar.getPolarity() == Polarity.INHIBITORY) {
                            probability =
                                calcConnectProb(src, tar, eiDistConst,
                                    lambda);
                        } else {
                            probability = calcConnectProb(src, tar, distConst,
                                lambda);
                        }
                    } else if (src.getPolarity() == Polarity.INHIBITORY) {
                        if (tar.getPolarity() == Polarity.EXCITATORY) {
                            probability =
                                calcConnectProb(src, tar, ieDistConst,
                                    lambda);
                        } else if (tar.getPolarity() == Polarity.INHIBITORY) {
                            probability =
                                calcConnectProb(src, tar, iiDistConst,
                                    lambda);
                        } else {
                            probability = calcConnectProb(src, tar, distConst,
                                lambda);
                        }
                    } else {
                        probability = calcConnectProb(src, tar, distConst,
                            lambda);
                    }
                    if (randVal < probability) {
                        count++;
                    }
                }
            }
            if (synapseGroup.isRecurrent()) {
                estimateDensity = (double) count / (synapseGroup
                    .getSourceNeuronGroup().size()
                    * (synapseGroup.getSourceNeuronGroup().size() - 1));
            } else {
                estimateDensity = (double) count / (synapseGroup.
                    getSourceNeuronGroup().size() * synapseGroup
                    .getTargetNeuronGroup().size());
            }
            synchronized (this) {
                notify();
            }
        }

        public double getDensityEsitmate() {
            return estimateDensity;
        }
    }

}
