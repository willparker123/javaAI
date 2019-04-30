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
		private final int mrXLastLocation = 0;

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
			moveIndex = moveScores.indexOf(moveScoresSorted.get(0)); //max score move

			//EXTRA DETECTIVE AI LOGIC
			if (!mrXAI && mrXLastLocation==view.getPlayerLocation(BLACK).get()) {
				callback.accept(new ArrayList<>(moves).get(scoreMovesMrXSecret(view, location, moves))); //picks max score move
			} else {
				callback.accept(new ArrayList<>(moves).get(moveIndex)); //picks max score move
			}
		}

		//scores moves for when mrX's last location is his location this round and returns the index of the original
		//		moveset to choose the optimal move from for a detective AI
		private int scoreMovesMrXSecret(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<Integer> sortedScoresMrX = scoreMoves(view, mrXLastLocation, getValidMoves(BLACK, mrXLastLocation, view));
			Collections.sort(sortedScoresMrX);
			ArrayList<Integer> scoresMrX = scoreMoves(view, mrXLastLocation, getValidMoves(BLACK, mrXLastLocation, view));
			ArrayList<Move> arrayMoves = new ArrayList<>(getValidMoves(BLACK, mrXLastLocation, view));
			Move bestMrXMove = arrayMoves.get(scoresMrX.indexOf(sortedScoresMrX.get(0)));
			int bestMoveIndex = 0;
			ArrayList<Integer> scores = new ArrayList<>();
			for (Move m : moves) {
				if (m instanceof TicketMove) {
					if (bestMrXMove instanceof TicketMove) {
						scores.add(criticalPath(view, ((TicketMove) bestMrXMove).destination(), location));
					} else if (bestMrXMove instanceof DoubleMove) {
						scores.add(criticalPath(view, ((DoubleMove) bestMrXMove).secondMove().destination(), location));
					} else scores.add(100);
				} else scores.add(100);
			}
			ArrayList<Integer> sortedScores = new ArrayList<>(scores);
			Collections.sort(sortedScores);
			return scores.indexOf(sortedScores.get(sortedScores.size()-1));
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

			try {
				if (m.colour().isMrX()) { //gets the number of valid moves from the move's destination (one-step ahead)
					totalValidMoves = getValidMoves(BLACK, m.destination(), view).size();
				} else totalValidMoves = getValidMoves(m.colour(), m.destination(), view).size();
			} catch (Exception e) {
				totalValidMoves = 1;
			}

			//for all players, find the distance to mrX from the player and total these distances up
			if (m.colour().isMrX()) { //AI MRX LOGIC
				List<Colour> cs = view.getPlayers();
				for (Colour c : cs) {
					//skip players with no location
					if (!view.getPlayerLocation(c).isPresent()) continue;
					else {
						//finds critical path to mrX from the player
						totalDistance+=criticalPath(view, m.destination(), view.getPlayerLocation(c).get());
					}
				}
			}	//AI DETECTIVE LOGIC
			else { //for detectives; find the distance to mrX
					if (!view.getPlayerLocation(view.getCurrentPlayer()).isPresent()) totalDistance+=0;
					else {
						//if mrx has a location
						if (view.getPlayerLocation(view.getPlayers().get(0)).isPresent()) {
							totalDistance+=criticalPath(view, m.destination(), view.getPlayerLocation(BLACK).get());
						} else { //mrx no location
							totalDistance+=1;
						}
					}
			}

			if (m.colour().isMrX()) {
				return totalDistance+totalValidMoves;
			} else {
				if (totalDistance!=0) {
					return totalValidMoves/totalDistance;
				} else {
					return totalValidMoves;
				}
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

		private int criticalPath(ScotlandYardView view, int x, int y) {
				int max = 0;
				for (Edge<Integer, Transport> e: view.getGraph().getEdgesFrom(view.getGraph().getNode(x))) {
					int s = 0;
					while (s<max) {
						if (e.destination().equals(view.getGraph().getNode(y))) s++;
						else if (criticalPath(view, e.destination().value(), y)<2) {
							s+=2;
						} else {
							s+=criticalPath(view, e.destination().value(), y);
						}
					}
					if (s>max) max = s;
				}
				return max;
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
