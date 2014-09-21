
package tv.acfun.video.util;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.HashMap;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import tv.acfun.util.net.Connectivity;
import tv.acfun.video.entity.Comment;
import tv.acfun.video.entity.Contents;
import tv.acfun.video.entity.User;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

public class MemberUtils {
    public static HashMap<String, Object> login(String host, String username, String password) throws HttpException, IOException,
            UnknownHostException, JSONException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        PostMethod post = new PostMethod("/login.aspx");
        NameValuePair[] nps = new NameValuePair[2];
        nps[0] = new NameValuePair("username", username);
        nps[1] = new NameValuePair("password", password);
        post.setRequestBody(nps);
        post.setRequestHeader("Content-Type", Connectivity.CONTENT_TYPE_FORM);
        HttpClient client = new HttpClient();
        client.getParams().setParameter("http.protocol.single-cookie-header", true);
        client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
        client.getHostConfiguration().setHost(host, 80, "http");
        int state = client.executeMethod(post);
        if (state > 200) {
            map.put("success", false);
            map.put("result", "ac娘大姨妈？");
            return map;
        }
        JSONObject re = JSON.parseObject(post.getResponseBodyAsString());
        if (!re.getBoolean("success")) {
            map.put("success", false);
            map.put("result", re.get("result"));
            return map;
        }
        Cookie[] cks = client.getState().getCookies();
        String uid = "";
        String avatar = "";
        String uname = "";
        String signature = "";
        for (Cookie ck : cks) {
            if (ck.getName().equals("auth_key")) {
                uid = ck.getValue();
            } else if (ck.getName().equals("ac_username")) {
                uname = URLDecoder.decode(ck.getValue(), "UTF-8");
            } else if (ck.getName().equals("ac_userimg")) {
                avatar = URLDecoder.decode(ck.getValue(), "UTF-8");
            }
        }
        if (TextUtils.isEmpty(uid)) {
            map.put("success", false);
            map.put("result", "登录失败");
            return map;
        }
        GetMethod getInfo = new GetMethod("/usercard.aspx?uid=" + uid);
        HttpState localHttpState = new HttpState();
        localHttpState.addCookies(cks);
        client.setState(localHttpState);
        client.executeMethod(getInfo);
        String jsonstring = getInfo.getResponseBodyAsString();
        JSONObject job = JSON.parseObject(jsonstring);
        if (job.getBoolean("success")) {
            JSONObject userjson = job.getJSONObject("userjson");
            signature = userjson.getString("sign");
            uname = userjson.getString("name");
        }
        User user = new User(Integer.parseInt(uid), uname, avatar, signature);
        user.cookies = JSON.toJSONString(cks, false);
        map.put("user", user);
        map.put("success", true);
        return map;
    }

    public static boolean postComments(String comment, int aid, String host, Cookie[] cks) throws HttpException, IOException {
        return postComments(comment, null, aid, host, cks);
    }

    public static boolean postComments(String comment, Comment quote, int aid, String host, Cookie[] cks) throws HttpException, IOException {
        PostMethod post = new PostMethod("/comment.aspx");
        NameValuePair[] nps = { new NameValuePair("name", "sendComm()"), new NameValuePair("name", "mimiko"),
                new NameValuePair("text", comment), new NameValuePair("quoteId", quote == null ? "0" : quote.cid + ""),
                new NameValuePair("contentId", String.valueOf(aid)), new NameValuePair("cooldown", "5000"),
                new NameValuePair("quoteName", quote == null ? "" : quote.userName) };
        post.setRequestBody(nps);
        post.setRequestHeader("Content-Type", Connectivity.CONTENT_TYPE_FORM);
        int state = Connectivity.doPost(post, host, cks);
        return state == 200;
    }

    public static boolean addFavourite(String cid, String host, Cookie[] cks) {
        NameValuePair[] nps = new NameValuePair[2];
        nps[0] = new NameValuePair("cId", cid);
        nps[1] = new NameValuePair("operate", "1");
        return Connectivity.postResultJson(host, "/member/collect.aspx", nps, cks).getBooleanValue("success");
    }

    public static boolean deleteFavourite(String cid, String host, Cookie[] cookies) {
        NameValuePair[] nps = new NameValuePair[2];
        nps[0] = new NameValuePair("cId", cid);
        nps[1] = new NameValuePair("operate", "0");
        return Connectivity.postResultJson(host, "/member/collect.aspx", nps, cookies).getBooleanValue("success");
    }

    public static JSONObject checkIn(String host, Cookie[] cks) {
        return Connectivity.postResultJson(host, "/member/checkin.aspx", null, cks);
    }

    /**
     * 
     * @param cookies
     * @param pageNo
     *            1~
     * @return
     */
    public static Contents getFavouriteOnline(String host, Cookie[] cookies, int pageNo) {
        return getFavouriteOnline(host, cookies, 20, pageNo);
    }

    public static Contents getFavouriteOnline(String host, Cookie[] cookies, int pageSize, int pageNo) {
        String result = Connectivity.doGet(host, "/member/collection.aspx",
                String.format("count=%d&pageNo=%d&channelId=0", pageSize, pageNo), cookies);
        if (TextUtils.isEmpty(result)) {
            return null;
        }
        return JSON.parseObject(result, Contents.class);
    }

    public static Contents getPushContents(String host, Cookie[] cookies, int pageNo) {
        String result = Connectivity.doGet(host, "/api/member.aspx",
                String.format("name=publishContent&isGroup=0&groupId=-1&pageSize=10&pageNo=%d", pageNo), cookies);
        if (TextUtils.isEmpty(result)) {
            return null;
        }
        return JSON.parseObject(result, Contents.class);
    }

    public static boolean checkFavourite(String host, Cookie[] cookies, int cid) {
        JSONObject result = Connectivity.getResultJson(host, "/member/collect_exist.aspx", String.format("cId=%d", cid), cookies);
        if (result != null) {
            try {
                return result.getBooleanValue("result");
            } catch (JSONException e) {}
        }
        return false;
    }
}
