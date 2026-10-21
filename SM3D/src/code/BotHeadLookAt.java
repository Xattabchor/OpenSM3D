package code;

import javax.microedition.m3g.Group;
import javax.microedition.m3g.Node;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.Transform;

/**
 * Поворот головы статичного NPC в сторону игрока.
 *
 * Порт фикса из Lekksii/OpenSM3D, переписанный под этот код: голова не
 * переориентируется "с нуля" (это затирало бы позу из анимации), а
 * заворачивается в промежуточную Group-пивот. Пивот ставится в точку головы,
 * сама голова сохраняет свой трансформ, поэтому поворот добавляется поверх
 * позы, а при target == 0 NPC снова смотрит ровно так, как его поставили.
 *
 * Порядок вызовов:
 *   Bot.loadStaticBot():     BotHeadLookAt.attachHead(botId, group)  // после animate(time)
 *   GameScene.renderWorld(): BotHeadLookAt.update(...)               // каждый кадр до graphics3D.render
 *   выгрузка комнаты:        BotHeadLookAt.detach(botId)
 */
public final class BotHeadLookAt {

    /** userID меша головы в pers.m3g (тело — 1..12) */
    public static final int HEAD_USER_ID = 2;

    private static final int MAX_BOTS = 10; // столько же, сколько staticBotMdlGroup

    private static final float MAX_YAW_DEG = 35.0F;   // предел поворота шеи влево-вправо
    private static final float MAX_PITCH_DEG = 18.0F; // предел наклона вверх-вниз
    private static final float TURN_SPEED_DEG_PER_SEC = 60.0F;
    /** Поправка на то, что модель смотрит не строго в +Z своей локальной системы */
    private static final float BODY_FORWARD_OFFSET_DEG = 15.0F;

    private static final Group[] headPivots = new Group[MAX_BOTS];
    private static final float[] currentYaw = new float[MAX_BOTS];
    private static final float[] currentPitch = new float[MAX_BOTS];

    private static final Transform tmpGroup = new Transform();
    private static final float[] tmpVector = new float[3];
    private static final float[] tmpPoint = new float[4];

    private BotHeadLookAt() {
    }

    /**
     * Находит голову NPC и оборачивает её в пивот. Вызывать после animate(time),
     * чтобы в пивот попала уже отыгранная поза.
     */
    public static void attachHead(int botId, Group botGroup) {
        detach(botId);
        if(botGroup == null) {
            return;
        }

        Node head = findDirectChild(botGroup, HEAD_USER_ID);
        if(head == null) {
            return;
        }

        head.getTranslation(tmpVector);

        Group pivot = new Group();
        pivot.setTranslation(tmpVector[0], tmpVector[1], tmpVector[2]);

        //Голова остаётся со своим поворотом/масштабом, из неё убирается
        //только смещение: оно теперь у пивота
        head.setTranslation(0.0F, 0.0F, 0.0F);
        botGroup.removeChild(head);
        pivot.addChild(head);
        botGroup.addChild(pivot);

        headPivots[botId] = pivot;
    }

    /** Сбрасывает состояние при выгрузке комнаты, чтобы следующий NPC не унаследовал угол. */
    public static void detach(int botId) {
        headPivots[botId] = null;
        currentYaw[botId] = 0.0F;
        currentPitch[botId] = 0.0F;
    }

    /**
     * @param botId          индекс в staticBotMdlGroup
     * @param botGroup       группа NPC (staticBotMdlGroup[botId])
     * @param bodyYawDeg     поворот тела NPC вокруг Y (staticBotYAngle[botId])
     * @param lookingAtPlayer true, пока прицел игрока наведён на этого NPC
     * @param deltaTimeMs    длительность кадра
     */
    public static void update(int botId, Group botGroup, float bodyYawDeg,
            boolean lookingAtPlayer, int deltaTimeMs) {
        Group pivot = headPivots[botId];
        if(pivot == null) {
            return;
        }

        float targetYaw = 0.0F;
        float targetPitch = 0.0F;

        if(lookingAtPlayer && botGroup != null) {
            //Позиция головы в мире: смещение пивота внутри группы,
            //прогнанное через трансформ группы (её родитель — gameWorld)
            pivot.getTranslation(tmpVector);
            tmpPoint[0] = tmpVector[0];
            tmpPoint[1] = tmpVector[1];
            tmpPoint[2] = tmpVector[2];
            tmpPoint[3] = 1.0F;

            botGroup.getTransform(tmpGroup);
            tmpGroup.transform(tmpPoint);

            float dx = GameScene.cameraPos[0] - tmpPoint[0];
            float dy = GameScene.cameraPos[1] - tmpPoint[1];
            float dz = GameScene.cameraPos[2] - tmpPoint[2];

            float worldYaw = signedAngleDeg(dx, dz);
            targetYaw = clamp(normalizeAngle(worldYaw - bodyYawDeg + BODY_FORWARD_OFFSET_DEG),
                    -MAX_YAW_DEG, MAX_YAW_DEG);

            float horizontalDist = (float) Math.sqrt(dx * dx + dz * dz);
            //Знак минус: положительный поворот вокруг +X опускает "взгляд"
            //модели, а смотреть надо вверх, когда камера выше головы
            float worldPitch = -signedAngleDeg(dy, horizontalDist);
            targetPitch = clamp(worldPitch, -MAX_PITCH_DEG, MAX_PITCH_DEG);
        }

        float maxStep = TURN_SPEED_DEG_PER_SEC * ((float) deltaTimeMs / 1000.0F);
        currentYaw[botId] = lerpTowards(currentYaw[botId], targetYaw, maxStep);
        currentPitch[botId] = lerpTowards(currentPitch[botId], targetPitch, maxStep);

        pivot.setOrientation(0.0F, 0.0F, 1.0F, 0.0F); //сброс поворота, смещение остаётся
        pivot.postRotate(currentYaw[botId], 0.0F, 1.0F, 0.0F);
        pivot.postRotate(currentPitch[botId], 1.0F, 0.0F, 0.0F);
    }

    private static Node findDirectChild(Group group, int userId) {
        for(int i = 0; i < group.getChildCount(); ++i) {
            Object3D child = group.getChild(i);
            if(child.getUserID() == userId && child instanceof Node) {
                return (Node) child;
            }
        }
        return null;
    }

    //В CLDC нет atan2: acos даёт угол 0..180 между вектором и осью Z,
    //знак берём по поперечной составляющей
    private static float signedAngleDeg(float opposite, float adjacent) {
        float hypotenuse = (float) Math.sqrt(opposite * opposite + adjacent * adjacent);
        if(hypotenuse == 0.0F) {
            return 0.0F;
        }
        float angle = (float) MathUtils.acos(adjacent / hypotenuse);
        return opposite < 0.0F ? -angle : angle;
    }

    /** Приводит угол к диапазону (-180, 180]. */
    private static float normalizeAngle(float deg) {
        while(deg > 180.0F) {
            deg -= 360.0F;
        }
        while(deg < -180.0F) {
            deg += 360.0F;
        }
        return deg;
    }

    private static float lerpTowards(float current, float target, float maxStep) {
        float diff = target - current;
        if(diff > maxStep) {
            diff = maxStep;
        } else if(diff < -maxStep) {
            diff = -maxStep;
        }
        return current + diff;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
