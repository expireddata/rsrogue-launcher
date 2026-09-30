/*
 * Copyright (c) 2026, rsrogue
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.rsrogue.launcher;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the launcher without RSProx: the client connects straight to the rsrogue game server.
 *
 * <p>RSProx normally serves the jav_config and world lists over http, and receives the client's
 * original RSA modulus over a unix socket. Here a small http server in the launcher serves them
 * instead, built from the server address, and the socket handshake is skipped.
 */
@Slf4j
public final class Standalone
{
	/** The game server players connect to. Overridden with {@code --server_host}. */
	public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
	public static final int DEFAULT_GAME_PORT = 43594;

	/** The public half of the server's RSA key pair ({@code .data/client.key}). */
	public static final String RSA_MODULUS =
		"bdf77c3d7c08ae862a61d005e9a760f1208d536e0922911f6b1671b753bba50aaf3e339a7b1e63fd20bd879281caa6ac987e919fe66774f04e8ebcb06c06717c9a7caefd6db127ed1263ea14debc371bf05db72a934e36bb36214665e7c1bd96c215cefe0617ddc5bd870a77fabb7356feb454187ff9ff378005171fc239efa9";

	/** RuneLite 1.11.19, the last build for game revision 233, from RSProx's mirror. */
	public static final String BOOTSTRAP_URL =
		"https://archive.rsprox.net/runelite/bootstrap/rewritten/6e7b0da812b64bba9ec8e6647f8acc436d9119274ac06b27f5abbcf33c04031e/bootstrap.json";

	/** The mirror's bootstrap is not signed, so the launcher checks it against this hash. */
	public static final String BOOTSTRAP_SHA256 = "097ec3220f507e95ceca376f7e44cd5eccd71aea9623a756f2b5759bb564ad01";

	public static final String CLIENT_NAME = "rsrogue";

	/**
	 * The patched RuneLite world client always fetches {@code http://127.1.45.2:<port>/worlds.js}
	 * ({@code WorldClientPatcher}), so the http server listens there.
	 */
	public static final String HTTP_HOST = "127.1.45.2";
	public static final int HTTP_PORT = 43580;

	public static final File HOME = new File(System.getProperty("user.home"), ".rsrogue");

	private static final int WORLD_ID = 255;
	private static final int WORLD_PROPERTIES = 0x02880001;
	private static final String WORLD_ACTIVITY = "rsrogue";

	private Standalone()
	{
	}

	/** Standalone unless RSProx started us, which always passes its socket id. */
	public static boolean isStandalone(String[] args)
	{
		return Arrays.stream(args).noneMatch(arg -> arg.startsWith("--socket_id"));
	}

	/**
	 * Adds the defaults RSProx would pass. Arguments already given win, so {@code --port} or
	 * {@code --server_host} can point a test build at another server.
	 */
	public static String[] withDefaults(String[] args)
	{
		List<String> result = new ArrayList<>(Arrays.asList(args));
		addDefault(result, "rsa", RSA_MODULUS);
		addDefault(result, "port", Integer.toString(DEFAULT_GAME_PORT));
		addDefault(result, "jav_config", "http://" + HTTP_HOST + ":" + HTTP_PORT + "/jav_config.ws");
		addDefault(result, "world_client_port", Integer.toString(HTTP_PORT));
		addDefault(result, "bootstrap_url", BOOTSTRAP_URL);
		addDefault(result, "client_name", CLIENT_NAME);
		addDefault(result, "server_host", DEFAULT_SERVER_HOST);
		if (!result.contains("--noupdate"))
		{
			result.add("--noupdate");
		}
		return result.toArray(new String[0]);
	}

	private static void addDefault(List<String> args, String name, String value)
	{
		String prefix = "--" + name;
		boolean present = args.stream().anyMatch(arg -> arg.equals(prefix) || arg.startsWith(prefix + "="));
		if (!present)
		{
			args.add(prefix + "=" + value);
		}
	}

	/**
	 * Starts the http server the client reads its config and world lists from. It has to live as
	 * long as the client, so the launcher waits for the client in standalone mode. If another
	 * launcher already serves the port, its server is used.
	 */
	public static void startHttpServer(String serverHost, int gamePort) throws IOException
	{
		String worldListUrl = "http://" + HTTP_HOST + ":" + HTTP_PORT + "/world_list.ws";
		byte[] javConfig = javConfig(serverHost, worldListUrl);
		byte[] worldList = worldList(serverHost);
		byte[] worldsJs = worldsJs(serverHost);

		HttpServer server;
		try
		{
			server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(HTTP_HOST), HTTP_PORT), 0);
		}
		catch (BindException e)
		{
			log.info("http port {} is in use, assuming another rsrogue launcher serves it", HTTP_PORT);
			return;
		}
		serve(server, "/jav_config.ws", javConfig);
		serve(server, "/world_list.ws", worldList);
		serve(server, "/worlds.js", worldsJs);
		server.start();
		log.info("Serving client config for {}:{} on http://{}:{}", serverHost, gamePort, HTTP_HOST, HTTP_PORT);
	}

	private static void serve(HttpServer server, String path, byte[] body)
	{
		server.createContext(path, exchange ->
		{
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody())
			{
				out.write(body);
			}
		});
	}

	private static byte[] javConfig(String serverHost, String worldListUrl) throws IOException
	{
		String template;
		try (InputStream in = Standalone.class.getResourceAsStream("jav_config.ws"))
		{
			if (in == null)
			{
				throw new IOException("jav_config.ws template missing");
			}
			template = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		}
		return template
			.replace("${server_host}", serverHost)
			.replace("${world_list_url}", worldListUrl)
			.getBytes(StandardCharsets.ISO_8859_1);
	}

	/** The client's binary world list (the login screen's world switcher): one world. */
	private static byte[] worldList(String serverHost) throws IOException
	{
		ByteArrayOutputStream worlds = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(worlds);
		out.writeShort(1);
		out.writeShort(WORLD_ID);
		out.writeInt(WORLD_PROPERTIES);
		writeJagString(out, serverHost);
		writeJagString(out, WORLD_ACTIVITY);
		out.writeByte(1);
		out.writeShort(0);

		ByteArrayOutputStream result = new ByteArrayOutputStream();
		DataOutputStream header = new DataOutputStream(result);
		header.writeInt(worlds.size());
		worlds.writeTo(result);
		return result.toByteArray();
	}

	private static void writeJagString(DataOutputStream out, String value) throws IOException
	{
		out.write(value.getBytes(StandardCharsets.ISO_8859_1));
		out.writeByte(0);
	}

	/** RuneLite's world list (its world hopper), in the api.runelite.net format. */
	private static byte[] worldsJs(String serverHost)
	{
		Map<String, Object> world = Map.of(
			"id", WORLD_ID,
			"types", List.of("MEMBERS"),
			"address", serverHost,
			"activity", WORLD_ACTIVITY,
			"location", 1,
			"players", 0
		);
		return new Gson().toJson(Map.of("worlds", List.of(world))).getBytes(StandardCharsets.UTF_8);
	}
}
