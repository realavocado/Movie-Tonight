package com.example.moviewebsite.movie_data.entitiesInDatabase.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.example.moviewebsite.movie_data.entitiesInDatabase.entity.BasicMovieInfo;
import com.example.moviewebsite.movie_data.entitiesInDatabase.repository.BasicMovieInfoRepository;
import com.example.moviewebsite.movie_data.objectsFromJson.movie.Movie;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

@Service
public class BasicMovieInfoService {
    private final BasicMovieInfoRepository basicMovieInfoRepository;

    //external service
    private final MovieGenrePairService movieGenrePairService;
    private final MovieCountryPairService movieCountryPairService;
    private final MovieCastPairService movieCastPairService;

    @Autowired
    public BasicMovieInfoService(BasicMovieInfoRepository basicMovieInfoRepository, MovieGenrePairService movieGenrePairService, MovieCountryPairService movieCountryPairService, MovieCastPairService movieCastPairService) {
        this.basicMovieInfoRepository = basicMovieInfoRepository;
        this.movieGenrePairService = movieGenrePairService;
        this.movieCountryPairService = movieCountryPairService;
        this.movieCastPairService = movieCastPairService;
    }

    public List<BasicMovieInfo> getMovieInfoByTitle(String movieTitle) {
        return basicMovieInfoRepository.findBasicMovieInfoByOriginalTitle(movieTitle);
    }

    public List<BasicMovieInfo> getMovieInfoByLanguage(String language) {
        return basicMovieInfoRepository.findBasicMovieInfoByLanguage(language);
    }

    public void addMovieInfoByCountry(String country) throws Exception {
        int page = 1;
        int totalPages = 5;
        for (int i = 0; i < totalPages; i++) {
            String uri = "https://streaming-availability.p.rapidapi.com/search/ultra?country=" + country +
                    "&services=netflix%2Cprime%2Cdisney%2Cstarz&type=movie&order_by=imdb_rating&page=" + page +
                    "&desc=true&output_language=en";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("X-RapidAPI-Key", "fb39dba226msh5347eb2352d5bc4p15a6e2jsnf95ec99de6bd")
                    .header("X-RapidAPI-Host", "streaming-availability.p.rapidapi.com")
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            //get total pages amount
            if (i == 0){
                int lastColonIndex = response.body().lastIndexOf(':');
                totalPages = Integer.parseInt(response.body().substring(lastColonIndex + 1, response.body().length()-1));
            }

            //split a large sets of json into an array
            int lastCommaIndex = response.body().lastIndexOf(',');
            String json = response.body().substring(11, lastCommaIndex);
            JSONArray arr = JSON.parseArray(json);

            //database operation
            for (int j = 0; j < arr.size(); j++) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Movie movie = mapper.readValue(arr.getString(j), Movie.class);
                    //save BasicMovieInfo
                    BasicMovieInfo basicMovieInfo = new BasicMovieInfo(
                            movie.getImdbID(), movie.getOriginalTitle(), movie.getOriginalLanguage(), movie.getYear(), movie.getOverview(), movie.getImdbRating(),
                            movie.getBackdropURLs().get1280(), movie.getBackdropURLs().get300(), movie.getBackdropURLs().get780(), movie.getBackdropURLs().getOriginal(),
                            movie.getPosterURLs().get154(), movie.getPosterURLs().get185(), movie.getPosterURLs().get342(), movie.getPosterURLs().get500(), movie.getPosterURLs().get780(), movie.getPosterURLs().get92(), movie.getPosterURLs().getOriginal(),
                            (movie.getStreamingInfo().getNetflix() == null)?null:movie.getStreamingInfo().getNetflix().getCountry().getLink(),
                            (movie.getStreamingInfo().getPrime() == null)?null:movie.getStreamingInfo().getPrime().getCountry().getLink(),
                            (movie.getStreamingInfo().getDisney() == null)?null:movie.getStreamingInfo().getDisney().getCountry().getLink(),
                            (movie.getStreamingInfo().getParamount() == null)?null:movie.getStreamingInfo().getParamount().getCountry().getLink(),
                            (movie.getStreamingInfo().getStarz() == null)?null:movie.getStreamingInfo().getStarz().getCountry().getLink()
                    );
                    Optional<BasicMovieInfo> basicMovieInfoOptional = basicMovieInfoRepository.findBasicMovieInfoByImdbID(basicMovieInfo.getImdbID());
                    if (basicMovieInfoOptional.isEmpty()){
                        saveMovieInfo(basicMovieInfo);
                    }
                    //save MovieGenrePair
                    movieGenrePairService.insertMovieGenrePairFromOneMovie(movie);
                    //save MovieCountryPair
                    movieCountryPairService.insertMovieCountryPairFromOneMovie(movie);
                    //save MovieCastPair
                    movieCastPairService.insertMovieCastPairFromOneMovie(movie);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            page++;
        }
    }

    public void addAllMovies() throws Exception {
        String[] countryArr = {"ar", "au", "at", "az", "be", "br", "bg", "ca", "cl", "co", "hr", "cy", "cz", "dk", "ec", "ee", "fl", "fr", "de", "gr", "hk", "hu", "is", "in", "id", "ie", "il", "it", "jp", "lt", "my", "mx", "md", "nl", "nz", "mk", "no", "pa", "pe", "ph", "pl", "pt", "ro", "ru", "rs", "sg", "za", "kr", "es", "se", "ch", "th", "tr", "ua", "ae", "gb", "us", "vn"};
        for (String country : countryArr) {
            addMovieInfoByCountry(country);
        }
    }

    @Transactional
    public void saveMovieInfo(BasicMovieInfo basicMovieInfo){
        basicMovieInfoRepository.save(basicMovieInfo);
    }

    @Transactional
    public void deleteMovieInfo(String imdbID) {
        basicMovieInfoRepository.deleteBasicMovieInfoByImdbID(imdbID);
    }

    @Transactional
    public void updateMovieName(String newTitle, String imdbID) {
        basicMovieInfoRepository.updateMovieTitle(newTitle, imdbID);
    }
}
