package ece.cpen502.Robots;
import ece.cpen502.LUT.*;
import robocode.*;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class MyRobot extends AdvancedRobot {
    private static LookupTable lut;
    // --------- game rounds record
    private static int totalNumRounds = 0;
    private static double numRoundsTo100 = 0;
    private static double numWins = 0;
    private double epsilon = 0.5;
    // --------- state record
    private int currentAction;
    private int currentState;
    private final LearningAgent.Algo currentAlgo = LearningAgent.Algo.QLearn;

    private int hasHitWall = 0;
    private int isHitByBullet = 0;
    // ---------- program components
    private LearningAgent agent;
    private EnemyRobot enemyTank;

    // -------- reward
    //the reward policy should be killed > bullet hit > hit robot > hit wall > bullet miss > got hit by bullet
    private double currentReward = 0.0;
    private final double goodReward = 5.0;
    private final double badReward = -2.0;
    private final double winReward = 10;
    private final double loseReward = -10;
    // TODO
    private int centerX;
    private int centerY;

    private double fireMagnitude;
    //
    Writer log;
    public void run() {

        // -------------------------------- Initialize robot tank parts ------------------------------------------------
        setBulletColor(Color.red);
        setGunColor(Color.green);
        setBodyColor(Color.yellow);
        setRadarColor(Color.blue);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true); // we need to adjust radar based on the distance and direction of the enemy tank
        enemyTank = new EnemyRobot();
        RobotState.initialEnergy = this.getEnergy();
        // -------------------------------- Initialize reinforcement learning parts ------------------------------------
        lut = new LookupTable();
        agent = new LearningAgent(lut);
        centerX = (int) getBattleFieldWidth()/2;
        centerY = (int) getBattleFieldHeight()/2;
        // ------------------------------------------------ Run --------------------------------------------------------

        while (true) {
            if (totalNumRounds > 10000) epsilon = 0;
            selectRobotAction();
            agent.train(currentState, currentAction, currentReward, currentAlgo);
            this.currentReward = 0;
            adjustAndFire();
        }
    }

    /**
     * selectRobotAction: select robot action based on robot current state
     */
    private void selectRobotAction(){
        int state = getRobotState();
        currentAction = agent.getAction(state, epsilon);
        this.resetState(); // reset hitWall hitByBullet
        switch(currentAction){
            case RobotAction.moveForward:
                setAhead(RobotAction.moveDistance);
                execute();
                break;
            case RobotAction.moveBack:
                setBack(RobotAction.moveDistance);
                execute();
                break;
            case RobotAction.headRight:
                setAhead(RobotAction.moveDistance);
                setTurnRight(90.0);
                execute();
                break;
            case RobotAction.headLeft:
                setAhead(RobotAction.moveDistance);
                setTurnLeft(90.0);
                execute();
                break;
            case RobotAction.backRight:
                setBack(RobotAction.moveDistance);
                setTurnRight(90.0);
                execute();
                break;
            case RobotAction.backLeft:
                setBack(RobotAction.moveDistance);
                setTurnLeft(90.0);
                execute();
                break;
        }
    }

    private void adjustAndFire() {
        fireMagnitude = 800 / enemyTank.distance;
        fireMagnitude = fireMagnitude > 3 ? 3 : fireMagnitude;
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        adjustGunAngle();
        if (getGunHeat() == 0) setFire(fireMagnitude);
        execute();
    }

    private void adjustGunAngle() {
        long bulletArrivalTime, bulletTransmissionTime;
        double coordinate[] = {enemyTank.xCoord, enemyTank.yCoord};
        for (int i = 0; i < 19; i++) {
            double distance = euclideanDistance(getX(), getY(), coordinate[0], coordinate[1]);
            bulletTransmissionTime = (int) Math.round((distance / (20 - (fireMagnitude * 3))));
            bulletArrivalTime = bulletTransmissionTime + getTime() - 9;
            coordinate = calculatePosition(bulletArrivalTime);
        }
        double gunOffset = getGunHeadingRadians() - (Math.PI / 2 - Math.atan2(coordinate[1] - getY(), coordinate[0] - getX()));
        setTurnGunLeftRadians(normalize(gunOffset));
    }

    private double normalize(double angle) {
        if (angle > Math.PI) angle -= 2*Math.PI;
        if (angle < -Math.PI) angle += 2*Math.PI;
        return angle;
    }

    private double[] calculatePosition(long bulletArrivalTime) {
        double difference = bulletArrivalTime - enemyTank.time;
        double coordinate[] = new double[2];
        coordinate[0] = enemyTank.xCoord + difference * enemyTank.velocity * Math.sin(enemyTank.heading);
        coordinate[1] = enemyTank.yCoord + difference * enemyTank.velocity * Math.cos(enemyTank.heading);
        return coordinate;
    }

    private double euclideanDistance(double x1, double y1, double x2, double y2) {
        double xDiff = x2 - x1, yDiff = y2 - y1;
        return Math.sqrt(Math.pow(xDiff, 2) + Math.pow(yDiff, 2));
    }

    private int getRobotState(){
        int curDistance = RobotState.calcDistanceState(enemyTank.distance);
        int enemyBearing = RobotState.getEnemyBearing(enemyTank.bearing);
        int curEnergy = RobotState.calcEnergyState(getEnergy());
        int heading = RobotState.getDirection(getHeading());
        currentState = RobotState.getState(curDistance, enemyBearing, heading, curEnergy, hasHitWall, isHitByBullet);
        return currentState;
    }

    private void resetState() {
        this.hasHitWall = 0;
        this.isHitByBullet = 0;
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        currentReward += goodReward;
    }

    @Override
    public void onBulletMissed(BulletMissedEvent event) {
        currentReward += badReward;
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e){
        isHitByBullet = 1;
        currentReward -= e.getBullet().getPower();
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        currentReward += badReward;
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        hasHitWall = 1;
        currentReward += badReward;
    }

    @Override
    public void onScannedRobot (ScannedRobotEvent e){
        double absoluteBearing = (getHeading() + e.getBearing()) % (360) * Math.PI/180;
        enemyTank.bearing = e.getBearingRadians();
        enemyTank.heading = e.getHeadingRadians();
        enemyTank.velocity = e.getVelocity();
        enemyTank.distance = e.getDistance();
        enemyTank.energy = e.getEnergy();
        enemyTank.xCoord = getX() + Math.sin(absoluteBearing) * e.getDistance();
        enemyTank.yCoord = getY() + Math.cos(absoluteBearing) * e.getDistance();
        enemyTank.time = getTime();
    }

    @Override
    public void onWin(WinEvent event) {
        currentReward += winReward;

        // TODO: record game
        if (numRoundsTo100 < 200) {
            System.out.println("win: " + numWins);
            numRoundsTo100++;
            numWins++;
        } else {
            System.out.println("\n\n !!!!!!!!! " +"win percentage"+ " " + ((numWins/numRoundsTo100) * 100) + "\n\n");
            numRoundsTo100 = 0;
            numWins = 0;
        }
        totalNumRounds++;
        agent.train(currentState, currentAction, currentReward, currentAlgo);
    }

    @Override
    public void onDeath(DeathEvent event) {
        currentReward += loseReward;

        // TODO: record game
        if(numRoundsTo100 < 200){
            numRoundsTo100++;
            System.out.println("lose: " + (numRoundsTo100 - numWins));
        }else{
            System.out.println("\n\n !!!!!!!!! " +"win percentage"+ " " + ((numWins/numRoundsTo100) * 100) + "\n\n");
            numRoundsTo100 = 0;
            numWins = 0;
        }

        totalNumRounds++;
        agent.train(currentState, currentAction, currentReward, currentAlgo);
    }
}
