package it.keyover.trsserver.tweet.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import it.keyover.trsserver.entity.Hashtag;
import it.keyover.trsserver.entity.Tweet;
import it.keyover.trsserver.entity.TwitterUser;
import it.keyover.trsserver.entity.User;
import it.keyover.trsserver.exception.BaseException;
import it.keyover.trsserver.factory.TwitterClientFactory;
import it.keyover.trsserver.lucene.service.ILuceneService;
import it.keyover.trsserver.mapper.HashtagEntityToHashtagMapper;
import it.keyover.trsserver.mapper.StatusToTweetMapper;
import it.keyover.trsserver.mapper.UserToTwitterUserMapper;
import it.keyover.trsserver.tweet.exception.HashtagAlreadExistException;
import it.keyover.trsserver.tweet.exception.NoTwitterUsersFound;
import it.keyover.trsserver.tweet.exception.RetrieveCategoriesException;
import it.keyover.trsserver.tweet.exception.RetrieveTweetsException;
import it.keyover.trsserver.tweet.exception.TweetAlreadyExistException;
import it.keyover.trsserver.tweet.exception.TweetsNotFoundException;
import it.keyover.trsserver.tweet.exception.TwitterUserAlreadyExistException;
import it.keyover.trsserver.tweet.exception.UserNotVerifiedException;
import it.keyover.trsserver.tweet.model.SearchDTO;
import it.keyover.trsserver.tweet.repository.HashtagRepository;
import it.keyover.trsserver.tweet.repository.TweetRepository;
import it.keyover.trsserver.tweet.repository.TwitterUserRepository;
import it.keyover.trsserver.user.repository.UserRepository;
import it.keyover.trsserver.util.PropertyReader;
import it.keyover.trsserver.util.SessionHelper;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

@Service
public class TweetService implements ITweetService {
	
	private static final Logger logger = LoggerFactory.getLogger(TweetService.class);
	private static final Twitter twitter = TwitterClientFactory.getTwitterClient();
	
	@Autowired
	private TweetRepository tweetRepository;
	//@Autowired
	//private HashtagRepository hashtagRepository;
	@Autowired
	private TwitterUserRepository twitterUserRepository;
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private ILuceneService luceneService;
	
	@Override
	public Integer retrieveTweetsFromCategory(String category) throws BaseException{
		Integer tweetsRetrieved = 0;
		
		List<TwitterUser> twitterUsers = twitterUserRepository.findByCategory(category);
		
		if(twitterUsers.isEmpty()) {
			throw new NoTwitterUsersFound();
		}
		
		for(TwitterUser twitterUser : twitterUsers) {
			logger.info("Fetching tweet for " + twitterUser.getName());
			tweetsRetrieved = Integer.sum(tweetsRetrieved, this.retrieveTweetsFromUser(twitterUser.getScreenName(), twitterUser.getCategory()));
		}
		
		return tweetsRetrieved;
	}

	@Override
	public Integer retrieveTweetsFromUser(String screenName, String category) throws BaseException{
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
	        	
	        	/*if(status.getHashtagEntities().length > 0) {
	        		for(HashtagEntity hashtag : status.getHashtagEntities()) {
		        		try{
		        			//this.saveHashtag(hashtag);
		        		}catch(BaseException e) {
		        			logger.info(e.getHrMessage());
		        		}
		        	}
	        	}*/

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

	/*@Override
	public String saveHashtag(HashtagEntity hashtag) throws BaseException {
		if(hashtagRepository.findByValue(hashtag.getText()).isPresent()) {
			throw new HashtagAlreadExistException();
		}
		
		Hashtag hashtagToSave = HashtagEntityToHashtagMapper.map(hashtag);

		return hashtagRepository.save(hashtagToSave).getId();
		return null;
	}*/
	
	@Override
	public Long saveTwitterUser(String screenName, String category) throws BaseException{		
		if(twitterUserRepository.findOneByScreenName(screenName).isPresent()) {
			throw new TwitterUserAlreadyExistException();
		}
		
		twitter4j.User user = null;
		try {
			user = twitter.showUser(screenName);
		} catch (TwitterException e) {
			logger.error(e.getErrorMessage());
			throw new UsernameNotFoundException(e.getErrorMessage());
		}
		TwitterUser twitterUserToSave = UserToTwitterUserMapper.map(user);
		twitterUserToSave.setCategory(category);

		return twitterUserRepository.save(twitterUserToSave).getId();
	}

	@Override
	public List<Tweet> getOneTweetByCategory() throws BaseException {
		String[] categories = PropertyReader.getCategories();
		
		List<Tweet> tweets = new ArrayList<Tweet>();
		
		for(String category : categories) {
			tweets.add(tweetRepository.findFirstByCategory(category));
		}
		
		if(tweets.isEmpty()) {
			throw new TweetsNotFoundException();
		}
		
		return tweets;
	}

	@Override
	public String[] getCategories() throws BaseException {
		String[] categories = PropertyReader.getCategories();
		
		if(categories.length == 0) {
			throw new RetrieveCategoriesException();
		}

		return categories;
	}

	@Override
	public List<Tweet> getTweetsByQueryAndCategory(SearchDTO search) throws BaseException {
		List<String> twitterids = luceneService.getTweetsTwitterIdFromIndex(search.getQuery(), search.getCategory(), search.getSearchType());	
		
		return tweetRepository.findByTwitteridIn(twitterids);
	}

	@Override
	public List<Tweet> getTweetsByQueryAndUserPreferences(SearchDTO search, User user)	throws BaseException {
		List<String> twitterids = new ArrayList<String>();
		for(String category : user.getPreferences()) {
			twitterids.addAll(luceneService.getTweetsTwitterIdFromIndex(search.getQuery(), category, search.getSearchType()));
		}
		
		return tweetRepository.findByTwitteridIn(twitterids);
	}

	@Override
	public Integer removeShortUrlTweets() {
		List<Tweet> tweets = (List<Tweet>) tweetRepository.findAll();
		Integer removed = 0;
		for(Tweet tweet : tweets) {
			if((tweet.getText().contains("http") && tweet.getText().length() < 30) || (tweet.getText().length() < 10)) {
				tweetRepository.delete(tweet);
				removed++;
			}
		}
		
		return removed;
	}	
}
