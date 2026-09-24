// Adds resistance-band back exercises (the catalog had no band pulling moves).
// Illustrations reuse the cable version of the same movement: identical motion, band instead of cable.
import fs from "node:fs";

const FILE = new URL("../data/exercises.json", import.meta.url);
const IMAGES = new URL("../public/exercise-images/", import.meta.url);
const exercises = JSON.parse(fs.readFileSync(FILE, "utf8"));

const NEW = [
  {
    id: "banded-seated-row", name: "Banded Seated Row", imageFrom: "seated-cable-row", level: "beginner", mechanic: "compound",
    primary: ["latissimus_dorsi", "rhomboids"], secondary: ["biceps_brachii", "posterior_deltoid", "trapezius"], bodyPart: "back",
    description: "A seated rowing pull with a resistance band looped around the feet, training the mid and upper back.",
    instructions: [
      "Sit on the floor with legs extended and loop the middle of the band around your feet.",
      "Hold an end in each hand with arms straight and chest tall.",
      "Pull the handles to your lower ribs, driving the elbows back and squeezing the shoulder blades together.",
      "Pause for a second, then let the arms extend slowly without rounding the back.",
    ],
    tips: ["Keep the torso still; the arms and back do the work.", "Choke up on the band to add resistance."],
  },
  {
    id: "banded-bent-over-row", name: "Banded Bent-Over Row", imageFrom: "cable-bent-over-row", level: "beginner", mechanic: "compound",
    primary: ["latissimus_dorsi", "rhomboids"], secondary: ["biceps_brachii", "posterior_deltoid", "erector_spinae"], bodyPart: "back",
    description: "A hip-hinged row standing on a resistance band, a home replacement for the barbell or dumbbell row.",
    instructions: [
      "Stand on the middle of the band with feet hip-width apart and hold one end in each hand.",
      "Hinge at the hips until the torso is about 45 degrees, knees soft and back flat.",
      "Row the hands to the hips, keeping the elbows close to the body.",
      "Lower under control and keep the hinge position throughout the set.",
    ],
    tips: ["Brace the core so the lower back stays neutral.", "A wider stance on the band increases the tension."],
  },
  {
    id: "banded-lat-pulldown", name: "Banded Lat Pulldown", imageFrom: "lat-pulldown", level: "beginner", mechanic: "compound",
    primary: ["latissimus_dorsi"], secondary: ["biceps_brachii", "rhomboids", "trapezius"], bodyPart: "back",
    description: "A vertical pull with a band anchored high on a door or bar, training the lats like a cable pulldown.",
    instructions: [
      "Anchor the band high on a closed door or a pull-up bar and kneel facing it.",
      "Grab the band with arms extended overhead and lean back slightly.",
      "Pull the hands down to the upper chest, driving the elbows toward the ribs.",
      "Return slowly until the arms are straight again.",
    ],
    tips: ["Think about pulling with the elbows, not the hands.", "Keep the ribs down; do not swing the torso."],
  },
  {
    id: "banded-straight-arm-pulldown", name: "Banded Straight-Arm Pulldown", imageFrom: "straight-arm-pulldown", level: "beginner", mechanic: "isolation",
    primary: ["latissimus_dorsi"], secondary: ["posterior_deltoid", "triceps_brachii"], bodyPart: "back",
    description: "A lat isolation pull with straight arms against a high-anchored band.",
    instructions: [
      "Anchor the band at head height or higher and stand facing it, one step back.",
      "Hold the band with straight arms in front of you at shoulder height.",
      "Sweep the arms down to the thighs while keeping the elbows almost straight.",
      "Let the arms rise back up slowly to shoulder height.",
    ],
    tips: ["Hinge slightly forward at the hips for a better lat stretch.", "Keep the shoulders down away from the ears."],
  },
  {
    id: "banded-face-pull", name: "Banded Face Pull", imageFrom: "face-pull", level: "beginner", mechanic: "compound",
    primary: ["posterior_deltoid", "rhomboids"], secondary: ["trapezius"], bodyPart: "shoulders",
    description: "A face-height pull with a band that trains the rear delts and upper back.",
    instructions: [
      "Anchor the band at face height and hold an end in each hand, palms facing down.",
      "Step back until the band is taut with arms straight.",
      "Pull the hands toward your face, splitting them apart with elbows high.",
      "Squeeze the upper back, then return slowly.",
    ],
    tips: ["Keep the elbows at shoulder height.", "Use a light band and control each rep."],
  },
];

for (const item of NEW) {
  if (exercises.some((e) => e.id === item.id)) continue;
  const dir = new URL(`${item.id}/`, IMAGES);
  fs.mkdirSync(dir, { recursive: true });
  for (const file of ["start.webp", "peak.webp"]) fs.copyFileSync(new URL(`${item.imageFrom}/${file}`, IMAGES), new URL(file, dir));
  exercises.push({
    id: item.id, name: item.name, force: "pull", level: item.level, mechanic: item.mechanic, equipment: "resistance_band",
    primaryMuscles: item.primary, secondaryMuscles: item.secondary, instructions: item.instructions, category: "strength",
    images: [`/exercise-images/${item.id}/start.webp`, `/exercise-images/${item.id}/peak.webp`],
    source: "repdb", sourceExerciseId: item.id, descriptionEn: item.description, tipsEn: item.tips, bodyPart: item.bodyPart,
    goalCompatibility: ["general_fitness", "muscle_gain", "fat_loss"], environment: ["home", "gym"], laterality: "bilateral",
    isBodyweight: false, metValue: 4, imageStart: `/exercise-images/${item.id}/start.webp`, imageEnd: `/exercise-images/${item.id}/peak.webp`,
    mediaStatus: "complete", isActive: true,
  });
}
fs.writeFileSync(FILE, `${JSON.stringify(exercises, null, 2)}\n`);
console.log(`Catalog now has ${exercises.length} exercises.`);
