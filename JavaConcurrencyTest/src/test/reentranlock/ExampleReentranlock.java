package test.reentranlock;

import static org.junit.Assert.*;

import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.tamu.aser.exploration.JUnit4MCRRunner;

@RunWith(JUnit4MCRRunner.class)
public class ExampleReentranlock {

	private static int x;
	private static int y;
	private static ReentrantLock lock = new ReentrantLock();
	
	
	
	public static void main(String[] args) {	
		Thread t1 = new Thread(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < 2; i++) {
					lock.lock();
					{
						x = 0;
					}
					lock.unlock();
					if (x > 0) {
						y++;
						x = 2;
					}
				}
			}

		});

		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 2; i++) {
					if (x > 1) {
						if (y == 3) {
							System.err.println("Find the error!");
//							fail();
						} else
							y = 2;
					}
				}
			}

		});
		t1.start();
		t2.start();

		for (int i = 0; i < 2; i++) {
			lock.lock();
			{
				x = 1;
				y = 1;
			}
			lock.unlock();
		}
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void test() throws InterruptedException {
		try {
			x = 0;
			y = 0;
//			lock = new Object();
			ExampleReentranlock.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}
}