package it.keyover.trsserver.tweet.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.keyover.trsserver.entity.Hashtag;
import it.keyover.trsserver.entity.Tweet;
import it.keyover.trsserver.entity.TwitterUser;
import it.keyover.trsserver.exception.BaseException;
import it.keyover.trsserver.factory.TwitterClientFactory;
import it.keyover.trsserver.mapper.HashtagEntityToHashtagMapper;
import it.keyover.trsserver.mapper.StatusToTweetMapper;
import it.keyover.trsserver.mapper.UserToTwitterUserMapper;
import it.keyover.trsserver.tweet.exception.HashtagAlreadExistException;
import it.keyover.trsserver.tweet.exception.NoTwitterUsersFound;
import it.keyover.trsserver.tweet.exception.RetrieveTweetsException;
import it.keyover.trsserver.tweet.exception.TweetAlreadyExistException;
import it.keyover.trsserver.tweet.exception.TwitterUserAlreadyExistException;
import it.keyover.trsserver.tweet.exception.UserNotVerifiedException;
import it.keyover.trsserver.tweet.repository.HashtagRepository;
import it.keyover.trsserver.tweet.repository.TweetRepository;
import it.keyover.trsserver.tweet.repository.TwitterUserRepository;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;

@Service
public class TweetService implements ITweetService {
	
	private static final Logger logger = LoggerFactory.getLogger(TweetService.class);
	private static final Twitter twitter = TwitterClientFactory.getTwitterClient();
	
	@Autowired
	private TweetRepository tweetRepository;
	@Autowired
	private HashtagRepository hashtagRepository;
	@Autowired
	private TwitterUserRepository twitterUserRepository;
	
	@Override
	public Integer retrieveTweets() throws BaseException{
		Integer tweetsRetrieved = 0;
		
		List<TwitterUser> twitterUsers = twitterUserRepository.findByVerified(true);
		
		if(twitterUsers.isEmpty()) {
			throw new NoTwitterUsersFound();
		}
		
		for(TwitterUser twitterUser : twitterUsers) {
			logger.info("Fetching tweet for " + twitterUser.getName());
			tweetsRetrieved = Integer.sum(tweetsRetrieved, this.retrieveTweet(twitterUser.getScreenName(), twitterUser.getCategory()));
			try {
				logger.info("Waiting for next user");
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return tweetsRetrieved;
	}

	@Override
	public Integer retrieveTweet(String screenName, String category) throws BaseException{
		try {		
			Integer tweetsRetrieved = 0;
			
			try {
				this.saveTwitterUser(screenName, category);
			}catch(BaseException e) {
				logger.info(e.getHrMessage());				
			}
			
			List<Status> statuses = twitter.getUserTimeline(screenName);
		    for (Status status : statuses) {
		        try {
		        	this.saveTweet(status, category);
		        	tweetsRetrieved++;
		        }catch(BaseException e) {
		        	logger.info(e.getHrMessage());
		        }
//	        	if(status.getUserMentionEntities().length > 0) {
//	        		for(UserMentionEntity userMentionEntity : status.getUserMentionEntities()) {
//	    		        try {
//	    		        	this.saveTwitterUser(userMentionEntity.getScreenName(), category);
//	    		        }catch(BaseException e) {
//	    		        	logger.info(e.getHrMessage());
//	    		        }
//	        		}
//	        	}
	        	
	        	if(status.getHashtagEntities().length > 0) {
	        		for(HashtagEntity hashtag : status.getHashtagEntities()) {
		        		try{
		        			this.saveHashtag(hashtag);
		        		}catch(BaseException e) {
		        			logger.info(e.getHrMessage());
		        		}
		        	}
	        	}

		    }		    
		    return tweetsRetrieved;
		} catch (TwitterException e) {
			throw new RetrieveTweetsException(e.getErrorMessage());
		}
	}
	
	@Override
	public Long saveTweet(Status tweet, String category) throws BaseException {
		if(tweetRepository.findById(tweet.getId()).isPresent()) {
			throw new TweetAlreadyExistException();
		}
		
		Tweet tweetToSave = StatusToTweetMapper.map(tweet);
		tweetToSave.setCategory(category);
		
		return tweetRepository.save(tweetToSave).getId();
	}

	@Override
	public String saveHashtag(HashtagEntity hashtag) throws BaseException {
		if(hashtagRepository.findByValue(hashtag.getText()).isPresent()) {
			throw new HashtagAlreadExistException();
		}
		
		Hashtag hashtagToSave = HashtagEntityToHashtagMapper.map(hashtag);

		return hashtagRepository.save(hashtagToSave).getId();
	}
	
	@Override
	public Long saveTwitterUser(String screenName, String category) throws BaseException, TwitterException{		
		if(twitterUserRepository.findOneByScreenName(screenName).isPresent()) {
			throw new TwitterUserAlreadyExistException();
		}
		
		User user = twitter.showUser(screenName);
		TwitterUser twitterUserToSave = UserToTwitterUserMapper.map(user);
		twitterUserToSave.setCategory(category);

		return twitterUserRepository.save(twitterUserToSave).getId();
	}
	
}
