import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int doctorNum = 0, patientNum = 0, seatNum = 0;
        // 读入命令行输入参数
        try {
            doctorNum = Integer.parseInt(args[0]);
            patientNum = Integer.parseInt(args[1]);
            seatNum = Integer.parseInt(args[2]);
        } catch (Exception e) {
            System.out.println("Error input!");
            System.exit(-1);
        }
        if (doctorNum <= 0 || patientNum <= 0 || seatNum <= 0) {
            System.out.println("Error input!");
            System.exit(-1);
        }
        /*
            原来打算用线程池，但是线程池给每个医生线程特定ID不会随着线程结束而清空，
            这一点不会实现。所以改用最普通的显示创建线程。以后有时间详细看一下线程池的实现，
         */
        // ExecutorService executor = Executors.newFixedThreadPool(doctorNum);
        // 创建阻塞队列
        ArrayBlockingQueue<Integer> seat = new ArrayBlockingQueue<Integer>(seatNum);
        var patients = new ArrayList<Thread>();
        var doctors = new ArrayList<Thread>();
        // 创建锁，用于保证stdout的输出有序
        Lock lock = new ReentrantLock();
        // 计数器使用atomic类保证线程安全，计数器统计治疗患者人数，用于判断何时结束医生进程
        AtomicInteger count = new AtomicInteger(0);
        // 创建医生线程
        for (int i = 0; i < doctorNum; i++) {
            Thread doctor = new Thread(new DoctorThread(seat, i, lock, count));
            // doctor.setPriority(10);
            doctor.start();
            doctors.add(doctor);
        }
        //创建病人线程
        for (int i = 0; i < patientNum; i++) {
            Thread patient = new Thread(new PatientThread(seat, i, lock));
            // patient.setPriority(1);
            patient.start();
            patients.add(patient);
        }
        // 回收病人线程
        for (Thread patient : patients) {
            patient.join();
        }
        // 当所有病人看病后，结束医生线程
        while (count.get() != patientNum) {
        }
        for (Thread doctor : doctors) {
            doctor.interrupt();
        }
        System.out.println("全部病人治疗完毕!");
        // System.out.println("interrupt");
        // executor.shutdown();
    }
}


// 医生线程
class DoctorThread implements Runnable {
    private final ArrayBlockingQueue<Integer> queue;
    private int doctorID;
    private Lock lock;
    private AtomicInteger count;

    DoctorThread(ArrayBlockingQueue<Integer> queue) {
        this.queue = queue;
    }

    DoctorThread(ArrayBlockingQueue<Integer> queue, int ID, Lock lock, AtomicInteger count) {
        this.queue = queue;
        this.doctorID = ID;
        this.lock = lock;
        this.count = count;
    }

    @Override
    public void run() {
        while (true) {
            try {
//            while (queue.size() != 0) {
//                System.out.println("医生" + doctorID + "空闲，休息500ms");
//                Thread.sleep(500);
//            }
                // System.out.println("医生闲" + lock);
                int treatTime = -1, patientID = -1;
                boolean flag = false;
                if (lock.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        // 防止出现死锁情况，如果队列是空且这时候调用take函数会进入死锁
                        if (queue.size() == 0) {
                            // lock.unlock();
                            continue;
                        }
                        // System.out.println(lock);
                        // System.out.println("doctor");
                        patientID = queue.take();
                        int seatNum = queue.remainingCapacity();
                        System.out.println("医生" + doctorID + "开始治疗病人" + patientID + "，病人离开座位，剩下座位:" + seatNum);
                        // Thread.sleep((int)(Math.random() * 1000)); //模拟治病需要时间
                        flag = true;
                        // 计数器加一
                        count.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }

                }
                // 如果有执行上面代码开始治疗病人，则打印病人治疗完成的信息。否则没有获得锁不打印
                if (flag) {
                    System.out.println("医生" + doctorID + "治疗完毕病人" + patientID);
                }
            } catch (InterruptedException e) {
                // System.out.println("医生" + doctorID +"下班了");
                // 主线程调用interrupt函数让医生线程结束，表示病人治疗完了
                return;
            }
        }
    }
}

class PatientThread implements Runnable {
    private final ArrayBlockingQueue<Integer> queue;
    private int patientID;
    private Lock lock;

    public PatientThread(ArrayBlockingQueue<Integer> queue, int ID, Lock lock) {
        this.queue = queue;
        this.patientID = ID;
        this.lock = lock;
    }

    @Override
    public void run() {
        while (true) {
            try {
//                while (queue.remainingCapacity() == 0) {
//                    sleepTime = (int) (Math.random() * 1000);
//                    System.out.println("座位满了，病人" + patientID + "过" + sleepTime + "ms再来");
//                }
                int seatRemain = -1;
                if (lock.tryLock(1, TimeUnit.SECONDS)) {
                    int sleepTime = 0;
                    try {
                        // System.out.println(lock);
                        // 如果队列满了则病人等一段随机时间再返回，判断是否能入队列
                        if (queue.remainingCapacity() == 0) {
                            sleepTime = (int) (Math.random() * 1000);
                            System.out.println("座位满了，病人" + patientID + "过" + sleepTime + "ms再来");
                            // Thread.sleep(sleepTime);
                            continue;
                        }
                        queue.put(patientID);
                        seatRemain = queue.remainingCapacity();
                        // System.out.println(seatRemain + "ff");
                        System.out.println("病人" + patientID + "坐到座位上待诊，剩下座位:" + seatRemain);
                        break;
                    } finally {
                        lock.unlock();
                        // 解锁之后判断是否因为座位满了要等待一段时间。要在解锁之后再调用sleep
                        if (sleepTime != 0) {
                            Thread.sleep(sleepTime);
                        }
                    }
                }
            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }
    /* 主要遇到记录：
        打印问题
        lock和take的死锁问题，以及lock和put的死锁问题
        结束医生线程
        classcastexception未解决
        线程池的名字初始化问题未解决

        学到的东西记录：
        廖雪峰

        仍有改善空间：
        使用线程池
        Atomic、ThreadLocal、Future、Callable
    */

}
