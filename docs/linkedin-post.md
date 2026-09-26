# LinkedIn post

Publication draft: use after the MIT license and prompt playbook are merged into
`main` and their public links have been verified. Copy the text below the divider.

---

🧠 The Weekend Project: N-Back

The first commit in my weekend game contained no gameplay.
It defined how the coding agent would work.

That turned out to be one of the most valuable parts of the project.

My focus in AI coding is shifting towards the environment around the model:
specifications, agent instructions, reusable skills, tools and verification.

N-back was my experiment. Remember whether the current item matches the one
shown N turns earlier. A simple rule that demands attention and continually
updating your working memory. Short sessions, adjustable difficulty and immediate
feedback made it an appealing game to build.

Before implementing the first session, we defined the awkward details: when a tap
counts, how scoring works, and what happens when the phone rotates or the app
loses focus.

We wrote out an entire example session whose correct result was 75%.
The code had to reproduce it.

That is specification-driven development—SDD—made concrete.

The agent had a clear target, examples to test against and boundaries for making
decisions. My sessions could run longer, with fewer course corrections, while
continuing in the intended direction.

The harness supported that autonomy: persistent project instructions, executable
checks, emulator tools, independent review and reusable workflows. We even
deliberately failed a test to prove the checks would catch it.

Then the app grew in complete steps: gameplay → practice → history → Position,
Colour and Number combinations → configurable sessions and progress charts.

From an empty repository to a working Android app on Google Play.

Spending longer on the specification helped me spend less time redirecting the
implementation. That is the workflow I want to repeat on my next project.

I've open-sourced the project under the MIT license—including the app, specs,
agent harness and skills.

I've also compiled a separate, ordered prompt playbook: from a blank directory
to a working app, with checkpoints for implementation, review and release.

📱 [Try N-Back on Google Play](https://play.google.com/store/apps/details?id=com.maswadkar.nback)

📖 [N-back on Wikipedia](https://en.wikipedia.org/wiki/N-back)

💻 [Explore the GitHub repository](https://github.com/lumensparkxy/nback-lab)

🛠️ [Read the prompt playbook](https://github.com/lumensparkxy/nback-lab/blob/main/docs/guides/blank-slate-to-working-app-prompts.md)

What would you build with this workflow over a weekend?

#AgenticCoding #SpecDrivenDevelopment #BuildInPublic
