package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.ai.ResourceProvider;
import uk.ac.bris.cs.scotlandyard.ai.Visualiser;
import uk.ac.bris.cs.scotlandyard.model.*;
import java.util.*;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;
import java.lang.Integer;

@ManagedAI("SAMI")
public class SamiAI implements PlayerFactory {

	@Override
	public Player createPlayer(Colour colour) {
		boolean isMrX = false;
		if (colour.isMrX()) {
			isMrX = true;
		} else isMrX = false;
		return new MyPlayer(isMrX);
	}

	private static class MyPlayer implements Player {
		//executes different move decider logic depending on if this is true or false
		private final boolean mrXAI;

		private final Random random = new Random();
		//bool that changes AI logic for a detective/mrX
		private MyPlayer(boolean isMrX) {
			mrXAI = isMrX;
		}

		//COMPARATOR/GENERICS/CALLBACKS: uses scoreMoves to get a list of scores; sort and pick the last/first element
		//									(max/min score for mrX/detective respectively) in moves and play that move.
		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
				Consumer<Move> callback) {
			ArrayList<Integer> moveScores = scoreMoves(view, location, moves);
			ArrayList<Integer> moveScoresSorted = moveScores;
			Collections.sort(moveScoresSorted);
			int moveIndex;

			if (mrXAI) {
				moveIndex = moveScores.indexOf(moveScoresSorted.get(0)); //max score move
			} else {
				moveIndex = moveScores.indexOf(moveScoresSorted.get(moveScores.size()-1)); //min score move
			}

			// picks a move
			callback.accept(new ArrayList<>(moves).get(moveIndex));
		}

		//scores moves (high = best move, low = worst move)
		private ArrayList<Integer> scoreMoves(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<Integer> scores = new ArrayList<>();
			//for each of the moves, score them and add the score to 'scores'
			Iterator<Move> iterator = moves.iterator();
			while (iterator.hasNext()) {
				scores.add(score(iterator.next(), view));
			}
			return scores;
		}

		//TODO
		private ArrayList<Integer> sortScores(ArrayList<Integer> scores) {
			return scores;
		}

		private Integer score(Move m, ScotlandYardView view) {
			//visitor
			if (m instanceof TicketMove) return score((TicketMove) m, view);
			else if (m instanceof PassMove) return score((PassMove) m, view);
			else if (m instanceof DoubleMove) return score((DoubleMove) m, view);
			else return 0;
		}
		//good score: far from detectives, many validMoves with target node
		private Integer score(TicketMove m, ScotlandYardView view) {
			int totalDistance = 0;
			int totalValidMoves = 0;
			//for all players, find the distance to mrX from the player and total these distances up
			if (mrXAI) {
				List<Colour> cs = view.getPlayers();
				for (Colour c : cs) {
					//skip players with no location
					if (!view.getPlayerLocation(c).isPresent()) continue;
					else {
						//finds critical path to mrX from the player
						totalDistance+=criticalPath(true, view, m.destination(), view.getPlayerLocation(c).get());
					}
				}
			} else { //for detectives; find the distance to mrX
					if (!view.getPlayerLocation(view.getCurrentPlayer()).isPresent()) totalDistance+=0;
					else {
						//finds critical path to mrX from the move's destination
						totalDistance+=criticalPath(false, view, m.destination(), view.getPlayerLocation(BLACK).get());
					}
			}

			try {
				if (mrXAI) { //gets the number of valid moves from the move's destination (one-step ahead)
					totalValidMoves = getValidMoves(BLACK, m.destination(), view).size();
				} else totalValidMoves = getValidMoves(m.colour(), m.destination(), view).size();
			} catch (Exception e) {
				totalValidMoves = 1;
			}

			if (mrXAI) {
				return totalDistance+totalValidMoves;
			}
			else {
				return totalDistance-totalValidMoves;
			}
		}
		private int score(DoubleMove m, ScotlandYardView view) {
			return score(m.secondMove(), view);
			//good score: far from detectives, many validMoves with target node
		}
		private int score(PassMove m, ScotlandYardView view) {
			return 0;
			//good score: far from detectives, many validMoves with target node
		}

		private int criticalPath(boolean maximising, ScotlandYardView view, int x, int y) {
			if (maximising) {
				int max = 0;
				for (Edge<Integer, Transport> e: view.getGraph().getEdgesFrom(view.getGraph().getNode(x))) {
					int s = 0;
					while (s<max) {
						if (e.destination().equals(view.getGraph().getNode(y))) s++;
						else if (criticalPath(true, view, e.destination().value(), y)<2) {
							s+=2;
						} else {
							s+=criticalPath(true, view, e.destination().value(), y);
						}
					}
					if (s>max) max = s;
				}
				return max;
			} else {
				int min = 0;
				for (Edge<Integer, Transport> e: view.getGraph().getEdgesFrom(view.getGraph().getNode(x))) {
					int s = 0;
					while (s>=min) {
						if (e.destination().equals(view.getGraph().getNode(y))) s++;
						else if (criticalPath(false, view, e.destination().value(), y)<2) {
							s+=2;
						} else {
							s+=criticalPath(false, view, e.destination().value(), y);
						}
					}
					if (s<min) min = s;
				}
				return min;
			}
		}

		public int roundRemaining(ScotlandYardView view) {
			return view.getRounds().size() - view.getCurrentRound();
		}

		public Set<Move> getValidMoves(Colour colour, Integer location, ScotlandYardView view) {
			Set<Move> cplayerMoves = new HashSet<>();
			Node<Integer> nodeL = view.getGraph().getNode(location);
			Collection<Edge<Integer, Transport>> e;

			if (nodeL == null) {
				e = view.getGraph().getEdges();
			} else {
				e = view.getGraph().getEdgesFrom(Objects.requireNonNull(nodeL));
			}

			for (Edge<Integer, Transport> edge : e) {
				Integer destination = edge.destination().value();
				Ticket ticket = Ticket.fromTransport(edge.data());
				//if the reachable nodes don't have a player on them and the player has available tickets,
				// add this node as a possible TicketMove
				if (!destinationHasPlayer(view,destination)) {
					if (playerHasTicketsAvailable(view,colour, ticket)) {
						cplayerMoves.add(new TicketMove(colour, ticket, destination));
					}
					if (playerHasTicketsAvailable(view,colour, SECRET)) {
						cplayerMoves.add(new TicketMove(colour, SECRET, destination));
					}
					if (playerHasTicketsAvailable(view,colour, DOUBLE) && roundRemaining(view) >= 2) {
						Node<Integer> nodeR = view.getGraph().getNode(destination);
						Collection<Edge<Integer, Transport>> d;
						if (nodeR == null) {
							d = view.getGraph().getEdges();
						} else {
							d = view.getGraph().getEdgesFrom(Objects.requireNonNull(nodeR));
						}
						for (Edge<Integer, Transport> edge1 : d) {
							Integer destination1 = edge1.destination().value();
							Ticket ticket1 = Ticket.fromTransport(edge1.data());
							boolean tickets = (ticket==ticket1 && playerHasTicketsAvailable(view,colour,ticket,2))
									|| (ticket != ticket1 && playerHasTicketsAvailable(view,colour,ticket1));
							TicketMove firstMove = new TicketMove(colour, ticket, destination);
							TicketMove secondMove = new TicketMove(colour, ticket1, destination1);

							if ((destination1 == location || !destinationHasPlayer(view,destination1)) && tickets) {
								cplayerMoves.add(new DoubleMove(colour, firstMove, secondMove));
							} if ((destination1 == location || !destinationHasPlayer(view,destination1))
									&& playerHasTicketsAvailable(view,colour,SECRET)) {
								TicketMove secretFirstMove = new TicketMove(colour,SECRET,destination);
								TicketMove secretSecondMove = new TicketMove(colour,SECRET,destination1);
								if (playerHasTicketsAvailable(view,colour,SECRET,2)) {
									cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secretSecondMove));
								} if (playerHasTicketsAvailable(view,colour,ticket)) {
									cplayerMoves.add(new DoubleMove(colour,firstMove,secretSecondMove));
								} if (playerHasTicketsAvailable(view,colour,ticket1)) {
									cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secondMove));
								}
							}
						}
					}
				}
			} if (colour.isDetective() && cplayerMoves.isEmpty()) {
				cplayerMoves.add(new PassMove(colour));
			}
			return cplayerMoves;
		}

		public boolean destinationHasPlayer(ScotlandYardView view, int i) {
			for (Colour p : view.getPlayers()) {
				if (view.getPlayerLocation(p).get()==i && p.isDetective()) return true;
			}
			return false;
		}

		public boolean playerHasTicketsAvailable(ScotlandYardView view, Colour colour, Ticket ticket) {
			int i;
			try {
				i = view.getPlayerTickets(colour, ticket).get();
			} catch (NoSuchElementException e) {
				i = 0;
			} if (i>=1) return true;
			else return false;
		}
		public boolean playerHasTicketsAvailable(ScotlandYardView view, Colour colour, Ticket ticket, int n) {
			int i;
			try {
				i = view.getPlayerTickets(colour, ticket).get();
			} catch (NoSuchElementException e) {
				i = 0;
			} if (i>=n) return true;
			else return false;
		}
	}
}
