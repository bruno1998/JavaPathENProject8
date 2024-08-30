package com.openclassrooms.tourguide;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    // TODO: Change this method to no longer return a List of Attractions.
    // Instead: Get the closest five tourist attractions to the user - no matter how far away they
    // are.
    // Return a new JSON object that contains:
    // Name of Tourist attraction,
    // Tourist attractions lat/long,
    // The user's location lat/long,
    // The distance in miles between the user's location and each of the attractions.
    // The reward points for visiting each Attraction.
    // Note: Attraction reward points can be gathered from RewardsCentral
    @RequestMapping("/getNearbyAttractions")
    public JSONArray getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);
        RewardCentral rewardCentral = new RewardCentral();
        JSONArray jsonArray = new JSONArray();
        for (Attraction attraction : attractions) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("attraction name", attraction.attractionName);
            jsonObject.put("attraction lat/long", attraction.latitude + "/" + attraction.longitude);
            jsonObject.put("user locaton lat/long",
                    visitedLocation.location.latitude + "/" + visitedLocation.location.longitude);
            jsonObject.put("distance between user and attraction",
                    rewardsService.getDistance(attraction, visitedLocation.location));
            jsonObject.put("reward point", rewardCentral.getAttractionRewardPoints(
                    attraction.attractionId, getUser(userName).getUserId()));
            jsonArray.put(jsonObject);
        }


        return jsonArray;
    }

    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }


}
