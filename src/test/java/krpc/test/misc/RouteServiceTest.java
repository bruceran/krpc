package krpc.test.misc;

import org.junit.Test;

import krpc.web.Route;
import krpc.web.WebUrl;
import krpc.web.impl.DefaultRouteService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class RouteServiceTest {

	@Test
	public void test1() throws Exception {
		
		DefaultRouteService impl = new DefaultRouteService();

		WebUrl ws1 = new WebUrl("*","/a/b","get",100,1);
		WebUrl ws2 = new WebUrl("*","/a/b","post",100,2);
		WebUrl ws3 = new WebUrl("*","/a/b/c","get",100,3);
		WebUrl ws4 = new WebUrl("*","/a/m","get",100,4);
		WebUrl ws5 = new WebUrl("*","/a/m/{nn}","get",100,5);
		WebUrl ws6 = new WebUrl("*","/a/d/{userId}/mm/{productId}","get",100,6);
		
		impl.addUrl(ws1);
		impl.addUrl(ws2);
		impl.addUrl(ws3);
		impl.addUrl(ws4);
		impl.addUrl(ws5);
		impl.addUrl(ws6);
		impl.init();
		
		Route r = null;

		r = impl.findRoute("a.com", "/c", "post");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a", "post");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/b", "get");
		assertEquals(1,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b", "post");
		assertEquals(2,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b", "put");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/b/d", "get");
		assertEquals(1,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b/c/m/d", "get");
		assertEquals(3,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b/c", "post");
		assertEquals(2,r.getMsgId()); // is 2, not 3
		
		r = impl.findRoute("a.com", "/a/m/ccc", "get");
		assertEquals(5,r.getMsgId());
		
		r = impl.findRoute("a.com", "/a/m/ccc/ddd", "get");
		assertEquals(5,r.getMsgId());
		System.out.println(r.getVariables());
		
		r = impl.findRoute("a.com", "/a/d/ccc/ddd/eee/fff", "get");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/d/ccc/mm/eee/fff", "get");
		assertEquals(6,r.getMsgId());
		System.out.println(r.getVariables());
	}

	@Test
	public void test2() throws Exception {
		
		DefaultRouteService impl = new DefaultRouteService(); 
		
		WebUrl ws1 = new WebUrl("a.com","/a/b","get",100,1);
		WebUrl ws2 = new WebUrl("a.com","/a/b","post",100,2);
		WebUrl ws3 = new WebUrl("a.com","/a/b/c","get",100,3);
		WebUrl ws4 = new WebUrl("a.com","/a/m","get",100,4);
		WebUrl ws5 = new WebUrl("a.com","/a/m/{nn}","get",100,5);
		WebUrl ws6 = new WebUrl("a.com","/a/d/{userId}/mm/{productId}","get",100,6);
		
		impl.addUrl(ws1);
		impl.addUrl(ws2);
		impl.addUrl(ws3);
		impl.addUrl(ws4);
		impl.addUrl(ws5);
		impl.addUrl(ws6);
		impl.init();
		
		Route r = null;

		r = impl.findRoute("a.com", "/c", "post");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a", "post");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/b", "get");
		assertEquals(1,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b", "post");
		assertEquals(2,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b", "put");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/b/d", "get");
		assertEquals(1,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b/c/m/d", "get");
		assertEquals(3,r.getMsgId());
		r = impl.findRoute("a.com", "/a/b/c", "post");
		assertEquals(2,r.getMsgId()); // is 2, not 3
		
		r = impl.findRoute("a.com", "/a/m/ccc", "get");
		assertEquals(5,r.getMsgId());
		
		r = impl.findRoute("a.com", "/a/m/ccc/ddd", "get");
		assertEquals(5,r.getMsgId());
		System.out.println(r.getVariables());
		
		r = impl.findRoute("a.com", "/a/d/ccc/ddd/eee/fff", "get");
		assertTrue(r == null);
		r = impl.findRoute("a.com", "/a/d/ccc/mm/eee/fff", "get");
		assertEquals(6,r.getMsgId());
		System.out.println(r.getVariables());
	}

}

