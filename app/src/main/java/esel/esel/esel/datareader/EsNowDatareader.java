package esel.esel.esel.datareader;

import java.io.Reader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import esel.esel.esel.util.CareService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;
import esel.esel.esel.util.ToastUtils;
import esel.esel.esel.util.UserLoginService;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class EsNowDatareader {

    private static final String TAG = "EsNowDatareader";
    private String username;
    private String password;
    static final  String grant_type = "password";
    static final  String client_id = "eversenseMMAAndroid";
    static final  String client_secret = "6ksPx#]~wQ3U";
    private String BASE_AUTH_URL = "https://ousiamapialpha.eversensedms.com/connect/";
    private String BASE_URL = "https://ousalphaapiservices.eversensedms.com/";
    static final String BASE_AUTH_URL_US = "https://apiservice.eversensedms.com/";
    static final String BASE_URL_US = "https://apiservice.eversensedms.com/";
    static final String BASE_AUTH_URL_OUS = "https://ousiamapialpha.eversensedms.com/connect/";
    static final String BASE_URL_OUS = "https://ousalphaapiservices.eversensedms.com/";
    private static DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static ZoneId zoneId = ZoneId.systemDefault();

    private UserLoginService.SenseonicsTokenDto token;
    private  List<CareService.UserProfileDto> user;
    private  List<CareService.CurrentValuesDto> values;
    private  List<CareService.UserEventDto> events;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ProcessResultI processor;


    private int userId = 0;

    private String bearer_token = "";
    private long token_expires=0;

    public EsNowDatareader() {

        username = SP.getString("es_username","");
        password =SP.getString("es_password","");

        BASE_AUTH_URL = BASE_AUTH_URL_OUS;
        BASE_URL = BASE_URL_OUS;

        if(SP.getBoolean("esdms_us",false)){
            BASE_AUTH_URL = BASE_AUTH_URL_US;
            BASE_URL = BASE_URL_US;
        }

        bearer_token = SP.getString("esnow_token", bearer_token);
        token_expires = SP.getLong("es_now_token_expire", token_expires);
        userId = SP.getInt("esnow_userId", userId);

    }

    static public void updateLogin(){
        EsNowDatareader reader = new EsNowDatareader();
        if(reader.bearer_token == "" || reader.tokenHasExpired() ) {
            reader.login();
        }
    }

    private void login() {
        LoginController login = new LoginController();
        login.start();;
    }

    private boolean tokenHasExpired(){
        long currentTime = System.currentTimeMillis();
        //return true;
        return  currentTime > token_expires;
    }

    private void currentUser() {
        UserController data = new UserController();
        data.start();
    }
    public void queryCurrentValue(ProcessResultI processor) {

        this.processor = processor;
            SgvController data = new SgvController();
            data.start();


    }

    public void queryLastValues(ProcessResultI processor,  int hours) {

            LocalDateTime.now();
            startDate = LocalDateTime.now().minusHours(hours);
            endDate = LocalDateTime.now();
            this.processor = processor;
            SgvHistController data = new SgvHistController();
            data.start();

    }


    private SGV generateSGV(CareService.UserEventDto data, int record){
        ZonedDateTime date = ZonedDateTime.parse(data.eventDate,dateformat.withZone(ZoneId.of("UTC")));
        long timestamp = (long)date.toEpochSecond() * 1000;
        int sgv = data.value;

        return new SGV(sgv,timestamp,record);
    }

    private SGV generateSGV(CareService.CurrentValuesDto data){
        ZonedDateTime date = ZonedDateTime.parse(data.timeStamp,dateformat.withZone(ZoneId.of("UTC")));
        long timestamp = (long)date.toEpochSecond() *1000;
        int sgv = data.currentGlucose;

        return new SGV(sgv,timestamp,1);
    }


    public class LoginController implements Callback<UserLoginService.SenseonicsTokenDto> {
        public void start() {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_AUTH_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            UserLoginService login = retrofit.create(UserLoginService.class);

            Call<UserLoginService.SenseonicsTokenDto> call = login.authenticate(grant_type,client_id,client_secret,username,password);
            call.enqueue(this);

        }

        @Override
        public void onResponse(Call<UserLoginService.SenseonicsTokenDto> call, Response<UserLoginService.SenseonicsTokenDto> response) {
            if (response.isSuccessful()) {
                token = response.body();
                if(token != null) {
                    token.SetExpireDateTime();
                    bearer_token = token.GetBearerToken();
                    token_expires = token.expireDateTime;
                    SP.putString("esnow_token",bearer_token );
                    SP.putLong("es_now_token_expire", token_expires);
                    currentUser();
                    EselLog.LogI(TAG,"Eversensedms: Successfully logged in.");
                }
            } else {
                try {
                    Reader reader = response.errorBody().charStream();
                    char[] charBuffer = new char[500];
                    int amountRead = reader.read(charBuffer);
                    charBuffer[amountRead] = '\0';
                    String msg = new String(charBuffer, 0, amountRead);
                    if(msg.contains("6008")){
                        EselLog.LogW(TAG,"Not authorized. Check username and password. Disabling Eversensedms...",true);
                        SP.putBoolean("use_esdms", false);
                    }else if(msg.contains("5005")) {
                        EselLog.LogW(TAG,"Not authorized. Check username and password. Account is locked. Try again in 30min. Disabling Eversensedms...",true);
                        SP.putBoolean("use_esdms", false);
                    }else{
                        EselLog.LogW(TAG,msg,true);
                    }


                }catch(Exception err){
                    EselLog.LogE(TAG,err.getMessage(),true);
                }
            }
        }

        @Override
        public void onFailure(Call<UserLoginService.SenseonicsTokenDto> call, Throwable t) {
            EselLog.LogE(TAG, t.getStackTrace().toString(), true);
        }
    }

    public class UserController implements Callback<List<CareService.UserProfileDto>> {
        public void start() {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            CareService data = retrofit.create(CareService.class);

            Call<List<CareService.UserProfileDto>> call = data.getUserProfile(bearer_token);
            call.enqueue(this);

        }

        @Override
        public void onResponse(Call<List<CareService.UserProfileDto>> call, Response<List<CareService.UserProfileDto>> response) {
            if (response.isSuccessful()) {
                user = response.body();
                if(user != null){
                    userId = user.get(0).userId;
                    SP.putInt("esnow_userId",userId );
                    EselLog.LogI(TAG,"UserId is " + userId);
                }
            } else {
                try {
                    EselLog.LogE(TAG, response.errorBody().string(), true);
                }catch(Exception err){
                    EselLog.LogE(TAG, err.getMessage(), true);
                }
            }
        }

        @Override
        public void onFailure(Call<List<CareService.UserProfileDto>> call, Throwable t) {
            EselLog.LogE(TAG, t.getStackTrace().toString(), true);
        }
    }


    public class SgvController implements Callback<List<CareService.CurrentValuesDto>> {


        public void start() {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            CareService data = retrofit.create(CareService.class);

            Call<List<CareService.CurrentValuesDto>> call = data.getCurrentValues(bearer_token,userId);
            call.enqueue(this);

        }

        @Override
        public void onResponse(Call<List<CareService.CurrentValuesDto>> call, Response<List<CareService.CurrentValuesDto>> response) {
            if (response.isSuccessful()) {
                values = response.body();
                List<SGV> result = new ArrayList<SGV>();
                SGV value = generateSGV(values.get(0));
                result.add(value);
                if(processor != null) {
                    processor.ProcessResult(result);
                }
            } else {
                try {
                    EselLog.LogE(TAG, response.errorBody().string(), true);
                }catch(Exception err){
                    EselLog.LogE(TAG, err.getMessage(), true);
                }
            }
        }

        @Override
        public void onFailure(Call<List<CareService.CurrentValuesDto>> call, Throwable t) {
            EselLog.LogE(TAG, t.getStackTrace().toString(), true);
        }
    }

    public class SgvHistController implements Callback<List<CareService.UserEventDto>> {

        public void start() {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            CareService data = retrofit.create(CareService.class);

            Call<List<CareService.UserEventDto>> call = data.getFollowingUserSensorGlucose(bearer_token,userId,startDate.format(dateformat),endDate.format(dateformat));
            call.enqueue(this);

        }

        @Override
        public void onResponse(Call<List<CareService.UserEventDto>> call, Response<List<CareService.UserEventDto>> response) {
            if (response.isSuccessful()) {
                events = response.body();
                List<SGV> result = new ArrayList<SGV>();
                for (int i = 0; i < events.size(); i++){
                    SGV value = generateSGV(events.get(i),i);
                    result.add(value);
                }
                if (processor != null){
                    processor.ProcessResult(result);
                }

            } else {
                try {
                    EselLog.LogE(TAG, response.errorBody().string(), true);
                }catch(Exception err){
                    EselLog.LogE(TAG, err.getMessage(), true);
                }
            }
        }

        @Override
        public void onFailure(Call<List<CareService.UserEventDto>> call, Throwable t) {
            EselLog.LogE(TAG, t.getStackTrace().toString(), true);
        }
    }

    public interface ProcessResultI{

        public void ProcessResult(List<SGV> data);

    }

}