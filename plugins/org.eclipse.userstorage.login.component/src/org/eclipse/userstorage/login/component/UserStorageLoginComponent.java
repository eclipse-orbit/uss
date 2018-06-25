/**
 *
 */
package org.eclipse.userstorage.login.component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.eclipse.userstorage.internal.util.IOUtil;
import org.eclipse.userstorage.internal.util.JSONUtil;
import org.eclipse.userstorage.login.service.IUserStorageLoginService;
import org.eclipse.userstorage.login.session.Session;
import org.eclipse.userstorage.login.user.User;
import org.eclipse.userstorage.session.service.IUserStorageSessionService;
import org.eclipse.userstorage.spi.Credentials;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * @author Zagrebaev_D
 *
 */

@Component(property= {
	"service.exported.interfaces:String=*"
	, "service.exported.configs:String=ecf.jaxrs.jersey.server"
	, "ecf.jaxrs.jersey.server.uri:String=http://localhost:8080/api/user"
	, "ecf.jaxrs.jersey.server.exported.interfaces=org.eclipse.userstorage.login.service.IUserStorageLoginService"}
	, immediate=true)
@Path("/")
public class UserStorageLoginComponent implements IUserStorageLoginService, IUserStorageSessionService {

	private final Map<String, User> users = new HashMap<>();

	private LoadingCache<String, Session> sessions;

	@Override
    @PUT
    @Path("login")
	public Response postLogin(InputStream creditianals) {

		Map<String, Object> requestObject = this.parseJson(creditianals);

		if (requestObject == null) {
			return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
		}

		String username = (String)requestObject.get("username"); //$NON-NLS-1$
		String password = (String)requestObject.get("password"); //$NON-NLS-1$

		User user = users.get(username);
		if (user == null || password == null || !password.equals(user.getPassword())) {
			return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
		}

		Session session = addSession(user);

//        NewCookie cookie = new NewCookie("SESSION", session.getID(), "/", "", "uss", 100000000, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		NewCookie cookie = new NewCookie(new Cookie("SESSION", session.getID(), "/", null)); //$NON-NLS-1$ //$NON-NLS-2$

		Map<String, Object> responseObject = new LinkedHashMap<>();
		responseObject.put("sessid", session.getID()); //$NON-NLS-1$
		responseObject.put("token", session.getCSRFToken()); //$NON-NLS-1$

		System.err.println(session.getID());

		StreamingOutput stream = buildResponseStream(responseObject);

		return Response.ok().entity(stream).type(MediaType.APPLICATION_JSON).cookie(cookie).build();
	}

	@Override
    @POST
    @Path("create")
	public Response postCreate(InputStream creditianals) {

		Map<String, Object> requestObject = this.parseJson(creditianals);

		if (requestObject == null) {
			return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
		}

		String username = (String)requestObject.get("username"); //$NON-NLS-1$
		String password = (String)requestObject.get("password"); //$NON-NLS-1$
		String confirmPassword = (String)requestObject.get("confirmPassword"); //$NON-NLS-1$

		if (this.users.containsKey(username) || !password.equals(confirmPassword)) {
			return Response.status(HttpServletResponse.SC_FORBIDDEN).build();
		}

		User user = addUser(new Credentials(username, password));

		Session session = addSession(user);
		NewCookie cookie = new NewCookie("SESSION", session.getID(), "/", "", "uss", 10, false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		return Response.status(HttpServletResponse.SC_CREATED).cookie(cookie).build();
	}

	public User addUser(Credentials credentials) {
		return addUser(credentials.getUsername(), credentials.getPassword());
	}

	public User addUser(String username, String password) {
		User user = new User(username, password);
		users.put(user.getUsername(), user);
		return user;
	}

	private Session addSession(User user) {
		Session session = new Session(user);
		sessions.put(session.getID(), session);
		return session;
	}

	@Activate
	public void activate(ComponentContext context) throws IOException {
		addUser(new Credentials("1", "1")); //$NON-NLS-1$ //$NON-NLS-2$

		this.sessions = CacheBuilder.newBuilder().concurrencyLevel(4).expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<String, Session>() {
			@Override
			public Session load(String key) throws Exception {
				// TODO Auto-generated method stub
				return null;
			}
		});
	}

	@Deactivate
	public void deactivate(ComponentContext context) {
		System.out.println("Login service stopped"); //$NON-NLS-1$
	}

	@Modified
	public void modify() {
		System.out.println("Login service modified"); //$NON-NLS-1$
	}

	@Override
	public boolean isAuth(String csrfToken, String sessionID) {
		return getSession(csrfToken, sessionID) != null ? true : false;
	}

	@Override
	public String getUserLogin(String sessionID) {
		Session session = sessions.getIfPresent(sessionID);
		return session == null ? null : session.getUser().getUsername();
	}

	private Session getSession(String csrfToken, String sessionID) {
		if (csrfToken != null && sessionID != null) {
			Session session = sessions.getIfPresent(sessionID);

			if (session != null && session.getCSRFToken().equals(csrfToken)) {
				return session;
			}
		}

		return null;
	}

	private Map<String, Object> parseJson(InputStream json) {

		Map<String, Object> requestObject = null;

		try {
			requestObject = JSONUtil.parse(json, null);
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return requestObject;
	}

	private StreamingOutput buildResponseStream(Map<String, Object> responseObject) {

		InputStream body = JSONUtil.build(responseObject);

		StreamingOutput stream = new StreamingOutput() {
			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException {
				IOUtil.copy(body, os);
				os.flush();
				IOUtil.closeSilent(body);
			}
		};

		return stream;
	}

}
