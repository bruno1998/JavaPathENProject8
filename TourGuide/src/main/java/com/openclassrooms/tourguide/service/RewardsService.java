package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations =
				new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = new CopyOnWriteArrayList<>(gpsUtil.getAttractions());
		List<UserReward> listUserReward = new CopyOnWriteArrayList<>(user.getUserRewards());

		List<Pair<VisitedLocation, Attraction>> locationAttractionPairs =
				userLocations.parallelStream()
						.flatMap(visitedLocation -> attractions.parallelStream()
								.filter(attraction -> listUserReward.parallelStream()
										.noneMatch(reward -> reward.attraction.attractionName
												.equals(attraction.attractionName)))
								.filter(attraction -> nearAttraction(visitedLocation, attraction))
								.map(attraction -> new Pair<>(visitedLocation, attraction)))
						.collect(Collectors.toList());

		List<VisitedLocation> visited =
				locationAttractionPairs.stream().map(Pair::getFirst).collect(Collectors.toList());

		List<Attraction> attac =
				locationAttractionPairs.stream().map(Pair::getSecond).collect(Collectors.toList());

		ExecutorService executorService = Executors.newFixedThreadPool(1000);

		List<CompletableFuture<Void>> futures =
				IntStream.range(0, visited.size())
						.mapToObj(i -> CompletableFuture.supplyAsync(
								() -> rewardsCentral.getAttractionRewardPoints(
										attac.get(i).attractionId, user.getUserId()),
								executorService).thenAccept(rewardPoints -> {
									user.addUserReward(new UserReward(visited.get(i), attac.get(i),
											rewardPoints));
								}).exceptionally(ex -> {
									ex.printStackTrace();
									return null;
								}))
						.toList();

		// List<CompletableFuture<Void>> futures = IntStream.range(0, visited.size())
		// .mapToObj(i -> getRewardPoints(attac.get(i), user).thenAccept(rewardPoints -> {
		// user.addUserReward(new UserReward(visited.get(i), attac.get(i), rewardPoints));
		// }).exceptionally(ex -> {
		// ex.printStackTrace();
		// return null;
		// })).toList();

		// Wait for all futures to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	public CompletableFuture<Integer> getRewardPoints(Attraction attraction, User user) {
		return CompletableFuture.supplyAsync(() -> rewardsCentral
				.getAttractionRewardPoints(attraction.attractionId, user.getUserId()));
	}

	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}


	private static class Pair<F, S> {
		private final F first;
		private final S second;

		public Pair(F first, S second) {
			this.first = first;
			this.second = second;
		}

		public F getFirst() {
			return first;
		}

		public S getSecond() {
			return second;
		}
	}
}
